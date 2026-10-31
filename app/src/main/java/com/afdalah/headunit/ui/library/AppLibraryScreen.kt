package com.afdalah.headunit.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.apps.AppCategory
import com.afdalah.headunit.ui.HeadUnitActions
import com.afdalah.headunit.ui.Screen
import com.afdalah.headunit.ui.components.AppIcon
import com.afdalah.headunit.ui.components.AvailableWhenParked
import com.afdalah.headunit.ui.components.CircleButton
import com.afdalah.headunit.ui.components.HuText
import com.afdalah.headunit.ui.components.huClickable
import com.afdalah.headunit.ui.theme.HuDimens
import com.afdalah.headunit.ui.theme.HuShapes
import com.afdalah.headunit.ui.theme.HuTheme

/** Categorized app drawer. Locked while moving (no long scrolling lists, no browsing). */
@Composable
fun AppLibraryScreen(moving: Boolean, actions: HeadUnitActions) {
    Column(Modifier.fillMaxSize().padding(HuDimens.gutter)) {
        ScreenHeader("App Library") { actions.openScreen(Screen.DASHBOARD) }
        if (moving) {
            AvailableWhenParked("Browse all apps once the car is stopped.", Modifier.fillMaxSize())
            return@Column
        }
        val app = LocalContext.current.app
        val all by app.apps.apps.collectAsStateWithLifecycle()
        val present = AppCategory.entries.filter { cat -> all.any { it.category == cat } }
        var selected by rememberSaveable { mutableStateOf<AppCategory?>(null) }
        val shown = all.filter { selected == null || it.category == selected }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { CategoryChip("All", selected == null) { selected = null } }
            items(present) { cat -> CategoryChip(cat.label, selected == cat) { selected = cat } }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing),
        ) {
            items(shown, key = { it.packageName }) { entry ->
                Column(
                    Modifier.huClickable(label = "Open ${entry.label}") { actions.launchApp(entry.packageName) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(entry.packageName, size = HuDimens.libraryIcon)
                    HuText(entry.label, HuTheme.type.callout, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = HuTheme.colors
    Box(
        Modifier
            .defaultMinSize(minHeight = HuDimens.minTarget, minWidth = 120.dp)
            .clip(HuShapes.pill)
            .background(if (selected) c.accent else c.fill)
            .huClickable(label = label, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        HuText(label, HuTheme.type.headline, color = if (selected) c.onAccent else c.labelPrimary, maxLines = 1)
    }
}

/** Back button + large title, shared by full-screen pages. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CircleButton(R.drawable.ic_chevron_left, "Back", size = HuDimens.minTarget, onClick = onBack)
        HuText(title, HuTheme.type.title1, maxLines = 1)
    }
}
