package dev.xiaoman.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xiaoman.agent.Persona
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.R
import dev.xiaoman.app.settings.ModelCatalog
import dev.xiaoman.app.settings.ModelOption
import dev.xiaoman.app.settings.ModelSource
import dev.xiaoman.app.settings.SettingsForm
import dev.xiaoman.llm.ModelListResult
import dev.xiaoman.llm.ModelListResult.Failure.Reason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The editable persona fields; saved into the `persona` / `profile` memory notes. */
private data class PersonaForm(
    val tone: String = "",
    val details: String = "",
    val identity: String = "",
)

@Composable
fun SettingsScreen(graph: AppGraph) {
    val store = graph.settings
    var form by remember { mutableStateOf(SettingsForm.of(store.current)) }
    var saved by remember { mutableStateOf(false) }
    val parsed = form.toSettings()
    val scope = rememberCoroutineScope()
    var persona by remember { mutableStateOf<PersonaForm?>(null) }
    var personaSaved by remember { mutableStateOf(false) }
    var personaError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val personaNote = graph.memory.read(Persona.PERMALINK)
        val profileNote = graph.memory.read(Persona.PROFILE_PERMALINK)
        persona = PersonaForm(
            tone = Persona.tone(personaNote),
            details = Persona.detailsText(personaNote),
            identity = Persona.identity(profileNote),
        )
    }

    val savePersona: () -> Unit = {
        val current = persona
        if (current != null) {
            val tone = current.tone.trim()
            val details = Persona.cleanDetails(current.details)
            val problem = if (tone.isEmpty() && details.isEmpty()) null else Persona.problem(tone, details)
            if (problem != null) {
                personaSaved = false
                personaError = true
            } else {
                scope.launch {
                    try {
                        if (tone.isEmpty() && details.isEmpty()) {
                            graph.memory.delete(Persona.PERMALINK)
                        } else {
                            graph.memory.write(Persona.note(tone, details, graph.memory.read(Persona.PERMALINK)))
                        }
                        val identity = current.identity.trim()
                        val profile = graph.memory.read(Persona.PROFILE_PERMALINK)
                        if (identity.isNotEmpty() || profile != null) {
                            graph.memory.write(Persona.profileNote(identity, profile))
                        }
                        personaSaved = true
                        personaError = false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        personaSaved = false
                        personaError = true
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

        PersonaSection(
            form = persona,
            saved = personaSaved,
            failed = personaError,
            onChange = {
                persona = it
                personaSaved = false
                personaError = false
            },
            onSave = savePersona,
        )
        HorizontalDivider()

        ModelSection(
            title = stringResource(R.string.settings_chat_model),
            model = form.chat,
            onChange = {
                form = form.copy(chat = it)
                saved = false
            },
            catalog = graph.modelCatalog,
            visionOnly = false,
            refreshModels = { baseUrl, key -> graph.modelListClient.list(baseUrl, key) },
        )
        HorizontalDivider()
        ModelSection(
            title = stringResource(R.string.settings_operator_model),
            model = form.operator,
            onChange = {
                form = form.copy(operator = it)
                saved = false
            },
            catalog = graph.modelCatalog,
            visionOnly = true,
            refreshModels = { baseUrl, key -> graph.modelListClient.list(baseUrl, key) },
        )
        TextButton(onClick = {
            form = form.copy(operator = form.operator.copy(baseUrl = form.chat.baseUrl, apiKey = form.chat.apiKey))
            saved = false
        }) { Text(stringResource(R.string.settings_copy_chat)) }
        HorizontalDivider()

        Text(stringResource(R.string.settings_agent), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = form.maxSteps,
            onValueChange = {
                form = form.copy(maxSteps = it)
                saved = false
            },
            label = { Text(stringResource(R.string.settings_max_steps)) },
            isError = !form.maxStepsValid,
            supportingText = { if (!form.maxStepsValid) Text(stringResource(R.string.settings_invalid_number)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_learn), modifier = Modifier.weight(1f))
            Switch(checked = form.learnFromSuccess, onCheckedChange = {
                form = form.copy(learnFromSuccess = it)
                saved = false
            })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = parsed != null, onClick = {
                parsed?.let {
                    store.save(it)
                    saved = true
                }
            }) { Text(stringResource(R.string.settings_save)) }
            if (saved) {
                Text(
                    stringResource(R.string.settings_saved),
                    modifier = Modifier.padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_folders), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_folder_skills, graph.skillsDir.path), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_folder_memory, graph.memoryDir.path), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_folder_traces, graph.tracesDir.path), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PersonaSection(
    form: PersonaForm?,
    saved: Boolean,
    failed: Boolean,
    onChange: (PersonaForm) -> Unit,
    onSave: () -> Unit,
) {
    Text(stringResource(R.string.settings_persona), style = MaterialTheme.typography.titleMedium)
    if (form == null) {
        Text(stringResource(R.string.settings_persona_loading), style = MaterialTheme.typography.bodySmall)
        return
    }
    OutlinedTextField(
        value = form.tone,
        onValueChange = { onChange(form.copy(tone = it)) },
        label = { Text(stringResource(R.string.settings_persona_tone)) },
        supportingText = { Text(stringResource(R.string.settings_persona_tone_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.details,
        onValueChange = { onChange(form.copy(details = it)) },
        label = { Text(stringResource(R.string.settings_persona_details)) },
        supportingText = { Text(stringResource(R.string.settings_persona_details_hint)) },
        minLines = 3,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.identity,
        onValueChange = { onChange(form.copy(identity = it)) },
        label = { Text(stringResource(R.string.settings_persona_identity)) },
        supportingText = { Text(stringResource(R.string.settings_persona_identity_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSave) { Text(stringResource(R.string.settings_persona_save)) }
        if (saved) {
            Text(
                stringResource(R.string.settings_persona_saved),
                modifier = Modifier.padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (failed) {
            Text(
                stringResource(R.string.settings_persona_invalid),
                modifier = Modifier.padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSection(
    title: String,
    model: SettingsForm.ModelForm,
    onChange: (SettingsForm.ModelForm) -> Unit,
    catalog: ModelCatalog,
    visionOnly: Boolean,
    refreshModels: suspend (baseUrl: String, apiKey: String) -> ModelListResult,
) {
    var showKey by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var remoteIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshResult by remember { mutableStateOf<ModelListResult?>(null) }
    var customEntry by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val options = catalog.options(model.model, remoteIds, visionOnly)
    val selected = options.firstOrNull { it.id == model.model.trim() }

    LaunchedEffect(model.baseUrl) {
        remoteIds = emptyList()
        refreshResult = null
    }

    Text(title, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = model.baseUrl,
        onValueChange = { onChange(model.copy(baseUrl = it)) },
        label = { Text(stringResource(R.string.settings_base_url)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (customEntry) {
        OutlinedTextField(
            value = model.model,
            onValueChange = { onChange(model.copy(model = it)) },
            label = { Text(stringResource(R.string.settings_model)) },
            supportingText = { Text(stringResource(R.string.settings_model_custom_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { customEntry = false }) {
            Text(stringResource(R.string.settings_model_choose_from_list))
        }
    } else {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.let { modelLabel(it) }.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_model)) },
                supportingText = { Text(supportingModelText(selected, catalog)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(modelLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (option.source != ModelSource.CUSTOM) {
                                    Text(
                                        option.id,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        trailingIcon = {
                            val mark = when {
                                option.source == ModelSource.BUILT_IN && option.vision ->
                                    R.string.settings_model_vision_mark
                                option.source == ModelSource.BUILT_IN -> R.string.settings_model_text_only_mark
                                else -> R.string.settings_model_vision_unknown_mark
                            }
                            Text(
                                stringResource(mark),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (option.vision) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            customEntry = false
                            onChange(model.copy(model = option.id))
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_model_custom_entry)) },
                    onClick = {
                        expanded = false
                        customEntry = true
                    },
                )
            }
        }
    }
    if (visionOnly && selected?.vision == false) {
        Text(
            stringResource(R.string.settings_model_vision_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            enabled = model.apiKey.isNotBlank() && !refreshing,
            onClick = {
                refreshing = true
                refreshResult = null
                scope.launch {
                    val result = refreshModels(model.baseUrl, model.apiKey)
                    refreshing = false
                    refreshResult = result
                    remoteIds = if (result is ModelListResult.Success) result.modelIds else emptyList()
                }
            },
        ) {
            Text(stringResource(if (refreshing) R.string.settings_model_refreshing else R.string.settings_model_refresh))
        }
        if (model.apiKey.isBlank()) {
            Text(stringResource(R.string.settings_model_refresh_need_key), style = MaterialTheme.typography.bodySmall)
        }
    }
    val result = refreshResult
    if (result != null) {
        val message = when (result) {
            is ModelListResult.Success -> stringResource(
                R.string.settings_model_refresh_ok,
                result.modelIds.size,
                catalog.options(model.model, remoteIds, visionOnly).size,
            )
            is ModelListResult.Failure -> when (result.reason) {
                Reason.UNAUTHORIZED -> stringResource(R.string.settings_model_refresh_unauthorized)
                Reason.UNSUPPORTED -> stringResource(R.string.settings_model_refresh_unsupported)
                Reason.NETWORK -> stringResource(R.string.settings_model_refresh_network)
                Reason.MALFORMED -> stringResource(R.string.settings_model_refresh_bad_body)
                Reason.OTHER -> stringResource(R.string.settings_model_refresh_other, result.httpCode ?: 0)
            }
        }
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (result is ModelListResult.Success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
    OutlinedTextField(
        value = model.apiKey,
        onValueChange = { onChange(model.copy(apiKey = it)) },
        label = { Text(stringResource(R.string.settings_api_key)) },
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
                Icon(
                    if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (showKey) R.string.settings_hide_key else R.string.settings_show_key),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = model.temperature,
        onValueChange = { onChange(model.copy(temperature = it)) },
        label = { Text(stringResource(R.string.settings_temperature)) },
        isError = !model.temperatureValid,
        supportingText = { if (!model.temperatureValid) Text(stringResource(R.string.settings_invalid_number)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun modelLabel(option: ModelOption): String =
    if (option.source == ModelSource.CUSTOM) {
        stringResource(R.string.settings_model_custom_prefix, option.id)
    } else {
        option.displayName
    }

@Composable
private fun supportingModelText(selected: ModelOption?, catalog: ModelCatalog): String = when {
    selected == null -> stringResource(R.string.settings_model_hint)
    selected.source == ModelSource.CUSTOM -> stringResource(R.string.settings_model_custom_hint)
    selected.source == ModelSource.REMOTE -> "${selected.id} · ${stringResource(R.string.settings_model_remote_no_vision)}"
    catalog.updatedAt.isNotBlank() ->
        "${selected.id} · ${stringResource(R.string.settings_model_built_in, catalog.updatedAt)}"
    else -> selected.id
}
