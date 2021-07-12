import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier

inline fun <T> Iterable<T>.averageOf(selector: (T) -> Int): Double {
	var sum = 0.0
	var count = 0
	for (element in this) {
		sum += selector(element)
		count++
	}
	return if (count == 0) Double.NaN else sum / count
}

@Composable
fun SimpleScrollbar(
	state: LazyListState,
	modifier: Modifier = Modifier,
	style: ScrollbarStyle = LocalScrollbarStyle.current
) {
	if (state.layoutInfo.visibleItemsInfo.isEmpty()) return

	val meanHeightOfVisibleItems = state.layoutInfo.visibleItemsInfo.averageOf { it.size }.toFloat()
	val approximateContainerHeight = meanHeightOfVisibleItems * state.layoutInfo.totalItemsCount
	val viewportHeight = state.layoutInfo.run { viewportEndOffset - viewportStartOffset }

	val firstVisibleItem = state.layoutInfo.visibleItemsInfo.first()
	val heightAboveVisibleBit = (firstVisibleItem.index * meanHeightOfVisibleItems) +
			(state.layoutInfo.viewportStartOffset - firstVisibleItem.offset)

	val offset = heightAboveVisibleBit / (approximateContainerHeight - viewportHeight)

	Box(modifier, BiasAlignment(0.0f,  offset * 2 - 1)) {
		Box(
			Modifier
				.background(style.unhoverColor, style.shape)
				.heightIn(min = style.minimalHeight)
				.fillMaxHeight(viewportHeight / approximateContainerHeight)
				.width(style.thickness)
		)
	}
}


fun IntRange.size(): Int = (last - start + 1).coerceAtLeast(0)

private class ComplexState(
	private val listState: LazyListState
) {
	val cachedRanges = mutableListOf<Pair<IntRange, Int>>()
	private var previousVisibleRange: IntRange? = null
	private var previousVisibleSize: Int? = null

	private val LazyListState.visibleRange: IntRange
		get() {
			val items = layoutInfo.visibleItemsInfo
			return if (items.isEmpty()) {
				IntRange.EMPTY
			} else {
				items.run { first().index .. last().index }
			}
		}

	fun update() {
		val visibleRange = listState.visibleRange
		val visibleSize = listState.layoutInfo.visibleItemsInfo.sumOf { it.size }

		if (previousVisibleRange != visibleRange) {
			cutOutVisibleRange()

			val prevRange = previousVisibleRange
			val prevSize = previousVisibleSize
			if (prevRange != null && prevSize != null) {
				// Add previously visible range
				val newPosition = cachedRanges.binarySearchBy(visibleRange.first) { (range, _) -> range.first }
				check(newPosition < 0) { "Visible range should not be cached. $newPosition, $visibleRange, $cachedRanges" }
				cachedRanges.add(-(newPosition + 1), prevRange to prevSize)
			}
			previousVisibleRange = visibleRange

			cutOutVisibleRange()

			performSimpleMergeAlgorithm()
		}
		previousVisibleSize = visibleSize
	}

	private fun cutOutVisibleRange() {
		val visibleRange = listState.visibleRange

		var index = 0
		while (index < cachedRanges.size) {
			val (range, _) = cachedRanges[index]
			// Is range before the current visible range?
			if (range.last < visibleRange.first) {
				index++
				continue
			}
			break
		}

		while (index < cachedRanges.size) {
			val (range, size) = cachedRanges[index]
			// Is range after the current visible range?
			if (visibleRange.last < range.first) {
				break
			}

			val sizeToTrim = listState.layoutInfo.visibleItemsInfo
				.asSequence()
				.filter { it.index in range }
				.sumOf { it.size }

			cachedRanges.removeAt(index)
			val remainingSize = size - sizeToTrim
			if (remainingSize <= 0) {
				continue
			}

			val newTopRange = range.first until visibleRange.first
			val newBottomRange = (visibleRange.last + 1) .. range.last

			if (!newTopRange.isEmpty()) {
				val newSize = (remainingSize * newTopRange.size()) / (newTopRange.size() + newBottomRange.size())
				cachedRanges.add(index++, newTopRange to newSize)
			}
			if (!newBottomRange.isEmpty()) {
				val newSize = (remainingSize * newBottomRange.size()) / (newTopRange.size() + newBottomRange.size())
				cachedRanges.add(index++, newBottomRange to newSize)
			}
		}
	}

	// TODO: This should be extracted out into an interface.
	private fun performSimpleMergeAlgorithm() {
		var index = 0
		while (index < cachedRanges.lastIndex) {
			val (range, size) = cachedRanges[index]
			val (nextRange, nextSize) = cachedRanges[index + 1]
			if (range.last + 1 == nextRange.first) {
				cachedRanges.removeAt(index)
				val newRange = range.first..nextRange.last
				val newSize = size + nextSize
				cachedRanges[index] = newRange to newSize
			} else {
				index++
			}
		}
	}
}

@Composable
fun ComplexScrollbar(
	state: LazyListState,
	modifier: Modifier = Modifier,
	style: ScrollbarStyle = LocalScrollbarStyle.current
) {
	val complexState = remember(state) { ComplexState(state) }
	val cachedRanges = complexState.cachedRanges

	if (state.layoutInfo.visibleItemsInfo.isEmpty()) {
		cachedRanges.clear()
		return
	}

	complexState.update()

	val totalKnownHeight = state.layoutInfo.visibleItemsInfo.sumOf { it.size } + cachedRanges.sumOf { (_, size) -> size }
	val totalKnownItems = state.layoutInfo.visibleItemsInfo.size + cachedRanges.sumOf { (range, _) -> range.size() }
	val meanHeightOfVisibleItems = totalKnownHeight / totalKnownItems.toFloat()

	val approximateContainerHeight = meanHeightOfVisibleItems * state.layoutInfo.totalItemsCount
	val viewportHeight = state.layoutInfo.run { viewportEndOffset - viewportStartOffset }

	val firstVisibleItem = state.layoutInfo.visibleItemsInfo.first()
	val knownBits = cachedRanges.takeWhile { (range, _) -> range.last < firstVisibleItem.index }
	val numberOfUnknownBits = firstVisibleItem.index - knownBits.sumOf { (range, _) -> range.size() }

	val heightAboveVisibleBit = knownBits.sumOf { (_, size) -> size } +
			(numberOfUnknownBits * meanHeightOfVisibleItems) +
			(state.layoutInfo.viewportStartOffset - firstVisibleItem.offset)

	val offset = heightAboveVisibleBit / (approximateContainerHeight - viewportHeight)

	Box(modifier, BiasAlignment(0.0f,  offset * 2 - 1)) {
		Box(
			Modifier
				.background(style.unhoverColor, style.shape)
				.heightIn(min = style.minimalHeight)
				.fillMaxHeight(viewportHeight / approximateContainerHeight)
				.width(style.thickness)
		)
	}
}


class ComplexScrollbarAdapter(
	private val listState: LazyListState
) : ScrollbarAdapter {
	private val complexState = ComplexState(listState)

	private val averageItemSize: Float
		get() {
			complexState.update()
			val totalSizeOfCachedItems = complexState.cachedRanges.sumOf { (_, size) -> size }
			val numberOfCachedItems = complexState.cachedRanges.sumOf { (range, _) -> range.size() }

			val visibleItems = listState.layoutInfo.visibleItemsInfo
			val totalSizeOfKnownItems = visibleItems.sumOf { it.size } + totalSizeOfCachedItems
			val numberOfKnownItems = visibleItems.size + numberOfCachedItems
			return totalSizeOfKnownItems.toFloat() / numberOfKnownItems
		}

	override val scrollOffset: Float
		get() {
			val firstVisibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0.0f

			val knownBits = complexState.cachedRanges.takeWhile { (range, _) -> range.last < firstVisibleItem.index }
			val numberOfUnknownBits = firstVisibleItem.index - knownBits.sumOf { (range, _) -> range.size() }

			return knownBits.sumOf { (_, size) -> size } +
					(numberOfUnknownBits * averageItemSize) +
					(listState.layoutInfo.viewportStartOffset - firstVisibleItem.offset)
		}

	override suspend fun scrollTo(containerSize: Int, scrollOffset: Float) {
		listState.scrollBy(scrollOffset - this.scrollOffset)
	}

	override fun maxScrollOffset(containerSize: Int): Float {
		val approximateContainerSize = averageItemSize * listState.layoutInfo.totalItemsCount
		// val viewportHeight = listState.layoutInfo.run { viewportEndOffset - viewportStartOffset }
		return approximateContainerSize - containerSize
	}
}
