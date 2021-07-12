import androidx.compose.desktop.Window
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val ITEM_COUNT = 50

enum class TypeOfItems(val desc: String, val items: List<Dp>) {
	Fixed(
		desc = "Items with a fixed size",
		items = List(ITEM_COUNT) { 30.dp }
	),
	Growing(
		desc = "Items gradually increasing in size",
		items = List(ITEM_COUNT) { ((it + 1) * 5).dp }
	),
	Shrinking(
		desc = "Items gradually decreasing in size",
		items = List(ITEM_COUNT) { ((it + 1) * 5).dp }.asReversed()
	),
}

fun main() = Window {
	MaterialTheme {
		val listState = rememberLazyListState()

		val itemsType by remember { mutableStateOf(TypeOfItems.Shrinking) }

		Row {
			LazyColumn(Modifier.weight(1.0f), state = listState) {
				itemsIndexed(itemsType.items) { idx, padding ->
					val backgroundColor = when (idx % 3) {
						0 -> Color.Cyan
						1 -> Color.Red
						2 -> Color.Green
						else -> Color.Gray
					}

					Text(
						"This is item number ${idx + 1}.",
						Modifier
							.background(backgroundColor)
							.padding(padding),
						textAlign = TextAlign.Center
					)
					Divider()
				}
			}

			Spacer(Modifier.width(8.dp))
			VerticalScrollbar(ScrollbarAdapter(listState), Modifier.fillMaxHeight())

			// Spacer(Modifier.width(8.dp))
			// SimpleScrollbar(listState, Modifier.fillMaxHeight())
			//
			// Spacer(Modifier.width(8.dp))
			// ComplexScrollbar(listState, Modifier.fillMaxHeight())

			Spacer(Modifier.width(8.dp))
			VerticalScrollbar(ComplexScrollbarAdapter(listState), Modifier.fillMaxHeight())
		}
	}
}
