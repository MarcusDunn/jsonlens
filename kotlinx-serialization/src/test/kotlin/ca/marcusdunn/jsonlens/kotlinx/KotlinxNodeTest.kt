package ca.marcusdunn.jsonlens.kotlinx

import ca.marcusdunn.jsonlens.model.JsonKind
import ca.marcusdunn.jsonlens.testsupport.Requirement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Requirement("lib/kotlinx-object-model")
class KotlinxNodeTest {

    private fun source(make: () -> Shape): ShapeSource = object : ShapeSource() {
        override fun resolve(): Shape = make()
    }

    @Test
    fun aKnownShapeNeedsNoSource() {
        val shape = Shape.scalar(JsonKind.TRUE, "true")
        val node = KotlinxNode(true, shape)
        assertSame(shape, node.shape())
        assertSame(shape, node.shape())
    }

    @Test
    fun theSourceRunsOnceAndLaterReadsGiveTheSameShape() {
        val calls = AtomicInteger()
        val node = KotlinxNode("x", source { calls.incrementAndGet(); Shape.scalar(JsonKind.STRING, "x") })
        val first = node.shape()
        assertSame(first, node.shape())
        assertEquals(1, calls.get())
    }

    /**
     * Thread A captures the shape and holds the lock. Thread B reads the state before A has a
     * shape, and waits for the lock. When B gets the lock, it must use the shape of A, not run
     * the source again: else the two threads would see different children.
     */
    @Test
    fun twoThreadsGetTheSameShape() {
        val calls = AtomicInteger()
        val inSource = CountDownLatch(1)
        val release = CountDownLatch(1)
        val node = KotlinxNode("x", source {
            calls.incrementAndGet()
            inSource.countDown()
            release.await(10, TimeUnit.SECONDS)
            Shape.scalar(JsonKind.STRING, "x")
        })
        val fromA = AtomicReference<Shape>()
        val fromB = AtomicReference<Shape>()
        val a = Thread { fromA.set(node.shape()) }
        a.start()
        assertTrue(inSource.await(10, TimeUnit.SECONDS))
        val b = Thread { fromB.set(node.shape()) }
        b.start()
        // B waits for the lock that A holds.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (b.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.onSpinWait()
        }
        assertEquals(Thread.State.BLOCKED, b.state)
        release.countDown()
        a.join(10_000)
        b.join(10_000)
        assertSame(fromA.get(), fromB.get())
        assertEquals(1, calls.get())
    }
}
