package com.watermelon.app

import kotlin.runCatching
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.state.lazyListStateOf
import androidx.compose.ui.state.lazyGridStateOf

/**
 * LaunchedEffect that scrolls a LazyList or LazyGrid to the top item.
 * 
 * Duplicate pattern found in 2 files:
 *   VideoListScreen.kt:192-194
 *   FolderBrowserScreen.kt:118-121
 * 
 * Original code:
 *   LaunchedEffect(currentSort, ascending, currentLayout, currentItemSize) {
 *       runCatching { listState.scrollToItem(0) }
 *       runCatching { gridState.scrollToItem(0) }
 *   }
 */
fun scrollToTop(
    listState: androidx.compose.foundation.lazy.rememberLazyListState.ListState?,
    gridState: androidx.compose.foundation.lazy.rememberLazyGridState.GridState?,
) {
    LaunchedEffect(
        listState?.hashCode() ?: 0,
        gridState?.hashCode() ?: 0
    ) {
        runCatching { listState?.scrollToItem(0) }
        runCatching { gridState?.scrollToItem(0) }
    }
}

/**
 * Overload with derived state of scrolling progress - only scrolls when not actively scrolling.
 */
fun scrollToTopSmooth(
    listState: androidx.compose.foundation.lazy.rememberLazyListState.ListState?,
    gridState: androidx.compose.foundation.lazy.rememberLazyGridState.GridState?,
    isScrolling: androidx.compose.runtime.state.State<Boolean>,
) {
    LaunchedEffect(listState?.hashCode() ?: 0, gridState?.hashCode() ?: 0, isScrolling) {
        if (!isScrolling.value) {
            runCatching { listState?.scrollToItem(0) }
            runCatching { gridState?.scrollToItem(0) }
        }
    }
}