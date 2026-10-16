// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MiyarMessengerContracts",
    platforms: [.macOS(.v13), .iOS(.v17)],
    products: [.library(name: "MiyarMessengerContracts", targets: ["MiyarMessengerContracts"])],
    targets: [
        .target(name: "MiyarMessengerContracts", path: "MiyarMessenger/Models"),
        .testTarget(name: "MiyarMessengerContractsTests", dependencies: ["MiyarMessengerContracts"], path: "Tests/CoreTests", resources: [.copy("Fixtures")])
    ]
)
