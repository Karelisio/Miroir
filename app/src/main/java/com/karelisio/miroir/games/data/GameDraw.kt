package com.karelisio.miroir.games.data

import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.model.ItemKind
import kotlin.random.Random

/** Ce qu'un tirage a le droit de proposer. */
data class DrawFilter(
    val kinds: Set<ItemKind>,
    val intensity: Int,
    val excluded: Set<String> = emptySet(),
    val formats: Set<ItemFormat>? = null,
    val remoteOnly: Boolean = false,
    val categories: Set<GameCategory>? = null,
)

/**
 * Tirage des entrées : intensité commune et exclusions respectées, catégories alternées, et pas de
 * répétition tant qu'une catégorie a des entrées jamais jouées (ensuite, les plus anciennes reviennent).
 */
object GameDraw {
    fun eligible(items: Collection<GameItem>, f: DrawFilter): List<GameItem> = items.filter {
        it.kind in f.kinds &&
            it.intensity <= f.intensity &&
            it.category.key !in f.excluded &&
            (f.categories == null || it.category in f.categories) &&
            (f.formats == null || it.format in f.formats) &&
            (!f.remoteOnly || it.remote || it.kind != ItemKind.ACTION)
    }

    fun draw(items: Collection<GameItem>, f: DrawFilter, seen: Map<String, Long>, n: Int, random: Random = Random.Default): List<GameItem> {
        val pools = eligible(items, f).groupBy { it.category }
            .mapValues { (_, list) ->
                // Jamais jouées d'abord (au hasard), puis les plus anciennement jouées.
                val (fresh, played) = list.partition { it.id !in seen }
                (fresh.shuffled(random) + played.sortedBy { seen[it.id] }).toMutableList()
            }
            .filterValues { it.isNotEmpty() }
            .toMutableMap()
        val out = mutableListOf<GameItem>()
        while (out.size < n && pools.isNotEmpty()) {
            // Catégories pondérées par ce qui leur reste de neuf, pour ne pas épuiser la plus petite d'abord.
            val weights = pools.mapValues { (_, list) -> list.count { it.id !in seen }.coerceAtLeast(1) }
            var pick = random.nextInt(weights.values.sum())
            val category = weights.entries.first { (_, w) -> (pick < w).also { if (!it) pick -= w } }.key
            val list = pools.getValue(category)
            out += list.removeAt(0)
            if (list.isEmpty()) pools.remove(category)
        }
        return out
    }
}
