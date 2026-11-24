package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.firebase.FirestoreSyncRepository.DriverApplication

/**
 * Application to become a KG Taxi driver. The owner checks it (documents in person or by
 * messenger for now) and gives the "driver" role in Firebase.
 */
@Composable
fun DriverApplicationDialog(
    status: String?,
    sending: Boolean,
    defaultName: String,
    defaultPhone: String,
    defaultCity: String,
    onSubmit: (DriverApplication) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Стать водителем KG Taxi") },
        text = {
            when (status) {
                "pending" -> Text(
                    "Ваша заявка на проверке. Мы свяжемся с вами по телефону. " +
                        "После одобрения режим водителя откроется сам (перезайдите в аккаунт)."
                )
                "rejected" -> Text("Заявка отклонена. Свяжитесь с поддержкой KG Taxi, чтобы узнать причину.")
                "approved" -> Text("Заявка одобрена! Выйдите и снова войдите в аккаунт, чтобы открыть режим водителя.")
                else -> ApplicationForm(sending, defaultName, defaultPhone, defaultCity, onSubmit)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Закрыть") } }
    )
}

@Composable
private fun ApplicationForm(
    sending: Boolean,
    defaultName: String,
    defaultPhone: String,
    defaultCity: String,
    onSubmit: (DriverApplication) -> Unit
) {
    var fullName by remember { mutableStateOf(defaultName) }
    var phone by remember { mutableStateOf(defaultPhone) }
    var city by remember { mutableStateOf(defaultCity) }
    var car by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var payPhone by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }

    val valid = fullName.trim().length >= 2 && phone.count { it.isDigit() } >= 9 &&
        car.trim().length >= 2 && plate.trim().length >= 4 && license.trim().length >= 4 &&
        experience.toIntOrNull() != null && agree

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Заполните анкету. Документы (права, техпаспорт) покажете при проверке.",
            style = MaterialTheme.typography.bodySmall
        )
        Field("ФИО", fullName, { fullName = it }, "app_full_name", KeyboardCapitalization.Words)
        Field("Телефон", phone, { phone = it }, "app_phone", keyboardType = KeyboardType.Phone)
        Field("Город", city, { city = it }, "app_city", KeyboardCapitalization.Words)
        Field("Машина: марка, модель, цвет", car, { car = it }, "app_car", KeyboardCapitalization.Sentences)
        Field("Госномер", plate, { plate = it.uppercase() }, "app_plate", KeyboardCapitalization.Characters)
        Field("Номер водительского удостоверения", license, { license = it }, "app_license", KeyboardCapitalization.Characters)
        Field("Стаж вождения, лет", experience, { experience = it.filter { c -> c.isDigit() }.take(2) }, "app_experience",
            keyboardType = KeyboardType.Number)
        Field("Номер MBank / O!Деньги для оплаты (необязательно)", payPhone, { payPhone = it }, "app_pay_phone",
            keyboardType = KeyboardType.Phone)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = agree, onCheckedChange = { agree = it }, modifier = Modifier.testTag("app_agree"))
            Text(
                "Согласен на проверку документов и хранение этих данных KG Taxi",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = {
                onSubmit(
                    DriverApplication(
                        fullName = fullName,
                        phone = phone,
                        city = city,
                        car = car,
                        plate = plate,
                        licenseNumber = license,
                        experienceYears = experience.toIntOrNull() ?: 0,
                        payPhone = payPhone
                    )
                )
            },
            enabled = valid && !sending,
            modifier = Modifier.fillMaxWidth().testTag("app_submit")
        ) {
            if (sending) CircularProgressIndicator(modifier = Modifier.padding0()) else Text("Отправить заявку")
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    tag: String,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(80)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

private fun Modifier.padding0(): Modifier = this
