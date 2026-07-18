import SwiftUI
import UIKit
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        if #available(iOS 16.0, *) {
            ComposeView()
                .ignoresSafeArea(.all)
                .persistentSystemOverlays(.hidden)
        } else {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
