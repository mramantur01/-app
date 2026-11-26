package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Turns what people type into +996XXXXXXXXX. Returns null if it can't be a Kyrgyz mobile number. */
fun normalizeKgPhone(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
    val national = when {
        digits.startsWith("996") && digits.length == 12 -> digits.drop(3)
        digits.startsWith("0") && digits.length == 10 -> digits.drop(1)
        digits.length == 9 -> digits
        else -> return null
    }
    // Kyrgyz mobile numbers are 9 digits and start with 2, 5, 7 or 9.
    if (national.first() !in "2579") return null
    return "+996$national"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Sign-in choice: by phone number with an SMS code (Firebase Phone Auth) or with Google.
 * After a successful sign-in the app's auth listener picks the user up as usual.
 *
 * Phone sign-in must be turned on in the Firebase console (Authentication → Sign-in method → Phone),
 * and the app's SHA-1/SHA-256 must be added to the Firebase project.
 */
/** SMS sign-in needs a paid Firebase plan (codes do not arrive on the free one). */
private const val SMS_SIGN_IN = false

@Composable
fun SignInDialog(
    onSignInWithGoogle: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phoneInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun signInWith(credential: PhoneAuthCredential) {
        busy = true
        scope.launch {
            try {
                Firebase.auth.signInWithCredential(credential).await()
                busy = false
                onDismiss()
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                busy = false
                error = "Неверный код. Проверьте SMS и попробуйте ещё раз."
            } catch (e: Exception) {
                busy = false
                error = "Не удалось войти: ${e.message ?: "нет интернета"}"
            }
        }
    }

    fun sendCode(resend: Boolean) {
        val phone = normalizeKgPhone(phoneInput)
        if (phone == null) {
            error = "Введите номер в формате 0555 123 456 или +996 555 123 456"
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            error = "Не удалось открыть проверку номера"
            return
        }
        busy = true
        error = null
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Android read the SMS by itself.
                signInWith(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                busy = false
                error = when (e) {
                    is FirebaseTooManyRequestsException -> "Слишком много попыток. Попробуйте позже."
                    is FirebaseAuthInvalidCredentialsException -> "Неверный номер телефона."
                    else -> "Не удалось отправить SMS: ${e.message ?: "ошибка"}"
                }
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                busy = false
                verificationId = id
                resendToken = token
            }
        }
        val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        val token = resendToken
        if (resend && token != null) builder.setForceResendingToken(token)
        Firebase.auth.setLanguageCode("ru")
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (verificationId == null) "Вход в KG Taxi" else "Код из SMS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (verificationId == null && !SMS_SIGN_IN) {
                    Text("Войдите через Google — это быстро и без SMS. Номер телефона укажете при регистрации.", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            onDismiss()
                            onSignInWithGoogle()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("sign_in_google")
                    ) { Text("Войти через Google") }
                } else if (verificationId == null) {
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it.take(20); error = null },
                        label = { Text("Номер телефона") },
                        placeholder = { Text("0555 123 456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("phone_input")
                    )
                    Button(
                        onClick = { sendCode(resend = false) },
                        enabled = !busy && phoneInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("send_sms_code")
                    ) { Text("Получить код по SMS") }
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onSignInWithGoogle()
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("sign_in_google")
                    ) { Text("Войти через Google") }
                } else {
                    Text(
                        "Мы отправили код на ${normalizeKgPhone(phoneInput) ?: phoneInput}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.filter { c -> c.isDigit() }.take(6); error = null },
                        label = { Text("6 цифр из SMS") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("sms_code_input")
                    )
                    Button(
                        onClick = {
                            val id = verificationId ?: return@Button
                            signInWith(PhoneAuthProvider.getCredential(id, codeInput))
                        },
                        enabled = !busy && codeInput.length == 6,
                        modifier = Modifier.fillMaxWidth().testTag("confirm_sms_code")
                    ) { Text("Войти") }
                    TextButton(onClick = { sendCode(resend = true) }, enabled = !busy) {
                        Text("Отправить код ещё раз")
                    }
                    TextButton(onClick = { verificationId = null; codeInput = "" }, enabled = !busy) {
                        Text("Изменить номер")
                    }
                }
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Закрыть") }
        }
    )
}
