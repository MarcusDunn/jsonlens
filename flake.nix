{
  description = "jsonlens: JSONPath, JSON Pointer, and JSON Patch for any JSON library in Java";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      devShells = forAllSystems (
        pkgs:
        let
          # The build JDK. The Gradle toolchain asks for this version.
          jdk = pkgs.jdk25;
        in
        {
          default = pkgs.mkShell {
            packages = [
              jdk
              (pkgs.gradle_9.override { java = jdk; })
            ];
            JAVA_HOME = jdk.home;
          };
        }
      );

      formatter = forAllSystems (pkgs: pkgs.nixfmt-rfc-style);
    };
}
