import SwiftUI

@MainActor
struct LoginView: View {
    @EnvironmentObject private var store: ChatStore
    @State private var username = ""
    @State private var password = ""
    @State private var code = ""
    @State private var nextPassword = ""
    @State private var confirmation = ""
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    Image(systemName: "bubble.left.and.bubble.right.fill").font(.system(size: 58)).foregroundStyle(MiyarTheme.accent).padding(.top, 54).accessibilityHidden(true)
                    VStack(spacing: 4) {
                        Text("مِعيار").font(MiyarTheme.font(36, weight: .bold, relativeTo: .largeTitle))
                        Text("محادثات تجمعنا").foregroundStyle(.secondary)
                    }
                    VStack(spacing: 16) {
                        if store.needsPasswordChange {
                            Text("أكملي إعداد كلمة مرور حسابك").font(MiyarTheme.font(weight: .semibold))
                            SecureField("كلمة المرور الحالية", text: $password).textContentType(.password)
                            SecureField("كلمة مرور جديدة — 10 أحرف على الأقل", text: $nextPassword).textContentType(.newPassword)
                            SecureField("تأكيد كلمة المرور", text: $confirmation).textContentType(.newPassword)
                        } else if store.needsMFA {
                            Text("أدخلي رمز تطبيق المصادقة").font(MiyarTheme.font(weight: .semibold))
                            TextField("رمز المصادقة أو الاسترداد", text: $code).textContentType(.oneTimeCode).textInputAutocapitalization(.never).autocorrectionDisabled()
                        } else {
                            TextField("اسم المستخدم أو رقم الطالبة", text: $username).textContentType(.username).textInputAutocapitalization(.never).autocorrectionDisabled()
                            SecureField("كلمة المرور", text: $password).textContentType(.password)
                        }
                        if let error = store.error { InlineError(text: error) }
                        if store.canRetrySession {
                            Button("إعادة الاتصال بالحساب المحفوظ", systemImage: "arrow.clockwise") { Task { await store.retrySession() } }.disabled(store.loading)
                        }
                        Button {
                            Task {
                                if store.needsPasswordChange { await store.changePassword(current: password, next: nextPassword, confirmation: confirmation) }
                                else { await store.login(username: username, password: password, code: code) }
                                if store.me != nil { password = ""; nextPassword = ""; confirmation = ""; code = "" }
                            }
                        } label: {
                            if store.loading { ProgressView().tint(.white).frame(maxWidth: .infinity) }
                            else { Text(store.needsPasswordChange ? "حفظ ومتابعة" : store.needsMFA ? "تحقق ومتابعة" : "تسجيل الدخول").frame(maxWidth: .infinity) }
                        }.buttonStyle(.borderedProminent).controlSize(.large)
                            .disabled(store.loading || (store.needsPasswordChange ? nextPassword.count < 10 || nextPassword != confirmation : store.needsMFA ? code.isEmpty : username.trimmingCharacters(in: .whitespaces).isEmpty || password.isEmpty))
                    }.textFieldStyle(.roundedBorder)
                    if store.needsMFA {
                        Button("العودة لتسجيل الدخول") { store.needsMFA = false; store.error = nil; code = ""; SessionVault.clear() }
                    }
                    Text("استخدمي بيانات حسابك في منصة معيار").font(MiyarTheme.font(13, relativeTo: .footnote)).foregroundStyle(.secondary)
                    Link("فتح منصة معيار", destination: URL(string: "https://deanju.tech")!).font(MiyarTheme.font(14))
                }.padding(24).frame(maxWidth: 520)
            }.scrollDismissesKeyboard(.interactively).background(MiyarTheme.surface)
        }
    }
}
