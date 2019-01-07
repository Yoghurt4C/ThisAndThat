/* This file is a part of the This & That project
 * by Juuxel, licensed under the MIT license.
 * Full code and license: https://github.com/Juuxel/ThisAndThat
 */
package juuxel.thisandthat.saw

import a2u.tn.utils.json.JsonParser
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceReloadListener
import net.minecraft.state.property.Property
import net.minecraft.tag.BlockTags
import net.minecraft.tag.ItemTags
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import org.apache.logging.log4j.LogManager
import java.util.concurrent.ThreadLocalRandom

object SawRecipeLoader : ResourceReloadListener {
    private const val directory = "saw_recipes"
    private val suffixes = arrayOf(".json5", ".json")
    private val logger = LogManager.getLogger()

    override fun onResourceReload(manager: ResourceManager) {
        manager.findResources(directory) {
            for (suffix in suffixes)
                if (it.endsWith(suffix))
                    return@findResources true

            false
        }.map(manager::getResource).forEach {
            SawRecipes.register(
                readRecipe(JsonParser.parse(it.inputStream.bufferedReader().lineSequence().joinToString(separator = "\n")))
                    ?: run {
                        logger.error("[ThisAndThat] Could not load saw recipe ${it.id}")
                        return@forEach
                    }
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readRecipe(map: Map<String, Any>): SawRecipe? {
        val transform = (map["output"] as? List<Any>)?.let(::readTransform) ?: return null
        val predicate = (map["predicate"] as? Map<String, Any>)?.let(::readPredicate) ?: return null

        return SawRecipe(predicate, transform)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readPredicate(map: Map<String, Any>): SawPredicate? {
        val type = map["type"]?.toString() ?: return null
        val id = Identifier(map["id"]?.toString() ?: return null)

        return when (type) {
            "tag" -> {{ it.block.matches(BlockTags.getContainer()[id]) }}
            "block" -> {{
                val state = map["state"] as? Map<String, Any> ?: emptyMap()
                Registry.BLOCK.getId(it.block) == id && state.isNotEmpty() && state.entries.all { (key, value) ->
                    it.entries.any {
                        it.key.getName() == key && (it.key as Property<in Comparable<Comparable<*>>>).getValueAsString(
                            it.value as Comparable<Comparable<*>>
                        ) == value
                    }
                }
            }}
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readTransform(list: List<Any>): SawTransform? {
        val functions = list.map {
            val map = it as? Map<String, Any> ?: return@map null
            val id = Identifier(map["id"]?.toString() ?: return@map null)
            val amount = map["amount"]?.toString()?.toIntOrNull() ?: 1

            when (map["type"]?.toString() ?: return@map null) {
                "tag_all" -> {{
                    ItemTags.getContainer()[id]?.entries()?.flatMap {
                        ArrayList<Item>().apply { it.build(this) }.map {
                            ItemStack(it, amount)
                        }
                    } ?: emptyList()
                }}

                "tag_random" -> {{
                    listOf(ItemStack(ItemTags.getContainer()[id]?.getRandom(ThreadLocalRandom.current()), amount))
                }}

                "item" -> {{
                    listOf(ItemStack(Registry.ITEM[id], amount))
                }}

                // :thonk:
                else -> null as (() -> List<ItemStack>)?
            }
        }

        return {
            functions.flatMap { it?.invoke() ?: emptyList() }
        }
    }
}
