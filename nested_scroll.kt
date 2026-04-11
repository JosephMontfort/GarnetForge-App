                val nestedScrollConnection = remember {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                            if (isDragging) { dragY = (dragY + available.y).coerceIn(-fullH, fullH); return available }
                            return Offset.Zero
                        }
                        override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                            if (available.y != 0f) { isDragging = true; dragY = (dragY + available.y).coerceIn(-fullH, fullH); return available }
                            return Offset.Zero
                        }
                        override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                            if (isDragging) { isDragging = false; if (kotlin.math.abs(dragY) > DISMISS_THRESHOLD) triggerDismiss() else dragY = 0f; return available }
                            return androidx.compose.ui.unit.Velocity.Zero
                        }
                        override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                            if (isDragging) { isDragging = false; if (kotlin.math.abs(dragY) > DISMISS_THRESHOLD) triggerDismiss() else dragY = 0f; return available }
                            return androidx.compose.ui.unit.Velocity.Zero
                        }
                    }
                }
