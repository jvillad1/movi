import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            // Movi tiene un solo tema, el claro (vuelta 29). Forzar el oscuro dejaba la barra de estado
            // en blanco sobre el crema; `UIUserInterfaceStyle = Light` del Info.plist hace lo mismo desde el arranque.
            .preferredColorScheme(.light)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
