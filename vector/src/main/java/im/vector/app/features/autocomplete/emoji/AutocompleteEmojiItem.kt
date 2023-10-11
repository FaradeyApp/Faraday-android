/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.autocomplete.emoji

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.extensions.setTextOrHide
import im.vector.app.core.glide.GlideApp
import im.vector.app.features.reactions.data.EmojiItem
import im.vector.app.features.themes.ThemeUtils
import org.matrix.android.sdk.api.extensions.orFalse

@EpoxyModelClass
abstract class AutocompleteEmojiItem : VectorEpoxyModel<AutocompleteEmojiItem.Holder>(R.layout.item_autocomplete_emoji) {

    @EpoxyAttribute
    lateinit var emojiItem: EmojiItem

    @EpoxyAttribute
    var emoteUrl: String? = null

    @EpoxyAttribute
    var emojiTypeFace: Typeface? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var onClickListener: ClickListener? = null

    override fun bind(holder: Holder) {
        super.bind(holder)
        if (emoteUrl?.isNotEmpty().orFalse()) {
            holder.emojiText.isVisible = false
            holder.emoteImage.isVisible = true
            holder.emoteImage.imageTintList = null
            GlideApp.with(holder.emoteImage)
                    .load(emoteUrl)
                    .centerCrop()
                    .into(holder.emoteImage)
        } else {
            holder.emojiText.text = emojiItem.emoji
            holder.emojiText.isVisible = true
            holder.emoteImage.isVisible = false
        }
        holder.emojiText.typeface = emojiTypeFace ?: Typeface.DEFAULT
        holder.emojiNameText.text = emojiItem.name
        holder.emojiKeywordText.setTextOrHide(emojiItem.keywords.joinToString())
        holder.view.onClick(onClickListener)
    }

    class Holder : VectorEpoxyHolder() {
        val emojiText by bind<TextView>(R.id.itemAutocompleteEmoji)
        val emoteImage by bind<ImageView>(R.id.itemAutocompleteEmote)
        val emojiNameText by bind<TextView>(R.id.itemAutocompleteEmojiName)
        val emojiKeywordText by bind<TextView>(R.id.itemAutocompleteEmojiSubname)
    }
}
