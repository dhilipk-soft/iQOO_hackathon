package com.studylens.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studylens.shared.QuizQuestion

@Composable
fun QuizScreen(
    questions: List<QuizQuestion>,
    onFinishQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Practice Quiz", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (questions.isNotEmpty()) {
            val q = questions.first()
            Text("Topic: ${q.topic}", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(q.question, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            q.options?.forEach { option ->
                RadioButtonOption(
                    optionText = option,
                    isSelected = selectedOption == option,
                    onSelect = { selectedOption = option }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onFinishQuiz,
            enabled = selectedOption != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Answer")
        }
    }
}

@Composable
private fun RadioButtonOption(
    optionText: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        RadioButton(selected = isSelected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(optionText, modifier = Modifier.padding(top = 12.dp))
    }
}
