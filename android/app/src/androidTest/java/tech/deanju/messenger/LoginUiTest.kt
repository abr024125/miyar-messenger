package tech.deanju.messenger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun loginIsArabicAndRequiresCredentialsWithoutSendingAnyRequest() {
        rule.onNodeWithText("مِعيار").assertIsDisplayed()
        rule.onNodeWithText("اسم المستخدم أو رقم الطالبة").assertIsDisplayed()
        rule.onNodeWithText("تسجيل الدخول").assertIsNotEnabled()
        rule.onNodeWithText("اسم المستخدم أو رقم الطالبة").performTextInput("ui-test-only")
        rule.onNodeWithText("تسجيل الدخول").assertIsNotEnabled()
        rule.onNodeWithText("كلمة المرور").performTextInput("not-a-real-password")
        rule.onNodeWithText("تسجيل الدخول").assertIsEnabled()
        rule.onNodeWithContentDescription("إظهار كلمة المرور").performClick()
        rule.onNodeWithContentDescription("إخفاء كلمة المرور").assertExists()
    }
}
