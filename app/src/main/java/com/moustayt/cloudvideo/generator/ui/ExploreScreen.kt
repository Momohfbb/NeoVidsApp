package com.moustayt.cloudvideo.generator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.Kind

data class Template(
    val cat: String,
    val kind: Kind,
    val style: String,
    val aspect: String,
    val emoji: String,
    val en: String,
    val ar: String,
)

val TEMPLATES = listOf(
    Template("ads", Kind.TEXT_TO_VIDEO, "cinematic", "9:16", "⌚", "Luxury watch slowly rotating on black marble, water droplets, dramatic rim light, product commercial", "ساعة فاخرة تدور ببطء على رخام أسود مع قطرات ماء وإضاءة جانبية درامية، إعلان منتج"),
    Template("ads", Kind.TEXT_TO_IMAGE, "realistic", "1:1", "☕", "Steaming cup of Arabic coffee with dates on a brass tray, morning sunlight, food photography", "فنجان قهوة عربية يتصاعد منه البخار مع تمر على صينية نحاسية، ضوء صباحي، تصوير طعام احترافي"),
    Template("ads", Kind.TEXT_TO_VIDEO, "3d", "9:16", "👟", "A sneaker exploding into floating parts then reassembling, colorful studio background, energetic ad", "حذاء رياضي ينفجر إلى أجزاء طائرة ثم يعود ليتجمّع، خلفية استوديو ملوّنة، إعلان حيوي"),
    Template("ads", Kind.TEXT_TO_IMAGE, "realistic", "1:1", "🌸", "Perfume bottle on silk among rose petals, soft pink light, luxury beauty campaign", "زجاجة عطر على قماش حريري بين بتلات الورد، ضوء وردي ناعم، حملة جمال فاخرة"),
    Template("nature", Kind.TEXT_TO_VIDEO, "realistic", "16:9", "🌊", "Drone shot over turquoise waves crashing on white sand at sunrise", "لقطة درون فوق أمواج فيروزية تتكسر على رمال بيضاء عند الشروق"),
    Template("nature", Kind.TEXT_TO_VIDEO, "cinematic", "16:9", "🏜️", "Camel caravan crossing golden dunes at sunset, long shadows, epic wide shot", "قافلة جمال تعبر كثبانًا ذهبية عند الغروب مع ظلال طويلة، لقطة واسعة ملحمية"),
    Template("nature", Kind.TEXT_TO_IMAGE, "realistic", "9:16", "🌌", "Milky way over a desert canyon, a lone tent glowing, long exposure astrophotography", "مجرة درب التبانة فوق وادٍ صحراوي وخيمة وحيدة مضيئة، تصوير فلكي بتعريض طويل"),
    Template("anime", Kind.TEXT_TO_VIDEO, "anime", "16:9", "🚲", "A girl riding a bicycle along a seaside road in summer, cherry blossoms flying, hand-drawn anime", "فتاة تقود دراجة على طريق ساحلي في الصيف وأزهار الكرز تتطاير، رسوم أنمي يدوية"),
    Template("anime", Kind.TEXT_TO_IMAGE, "anime", "9:16", "🐉", "Young hero facing a giant sky dragon above the clouds, dramatic anime key visual", "بطل شاب يواجه تنينًا سماويًا عملاقًا فوق الغيوم، ملصق أنمي درامي"),
    Template("cinema", Kind.TEXT_TO_VIDEO, "cyberpunk", "16:9", "🌃", "Slow dolly through a neon-lit rainy alley, reflections on wet ground, a figure in a long coat", "حركة كاميرا بطيئة في زقاق ممطر تضيئه أضواء النيون، انعكاسات على الأرض المبتلة وشخص بمعطف طويل"),
    Template("cinema", Kind.TEXT_TO_VIDEO, "vintage", "16:9", "🎞️", "1970s road trip, vintage car on an empty highway, warm film grain, nostalgic", "رحلة برية من السبعينيات، سيارة كلاسيكية على طريق فارغ، حبيبات فيلم دافئة وحنين"),
    Template("cinema", Kind.TEXT_TO_VIDEO, "cinematic", "16:9", "🚀", "Rocket launch at dawn seen from a distant hill, smoke clouds glowing orange, IMAX shot", "إطلاق صاروخ عند الفجر من تل بعيد، سحب دخان تتوهج بالبرتقالي، لقطة IMAX"),
    Template("heritage", Kind.TEXT_TO_VIDEO, "cinematic", "16:9", "🏮", "Old Damascus street at night lit by lanterns, light rain, slow camera glide", "شارع دمشقي قديم ليلًا تضيئه الفوانيس، مطر خفيف، والكاميرا تنساب ببطء"),
    Template("heritage", Kind.TEXT_TO_IMAGE, "realistic", "9:16", "🕌", "Grand mosque courtyard at golden hour, intricate arabesque tiles, pigeons in flight", "صحن مسجد كبير في الساعة الذهبية، زخارف أرابيسك دقيقة وحمام يطير"),
    Template("heritage", Kind.TEXT_TO_VIDEO, "cinematic", "16:9", "🐎", "Arabian horse galloping along the shore at sunset, slow motion, mane flowing", "حصان عربي يركض على الشاطئ عند الغروب بالحركة البطيئة وعرفه يتطاير"),
    Template("heritage", Kind.TEXT_TO_IMAGE, "watercolor", "1:1", "🌙", "Ramadan night, crescent moon over rooftops, hanging fanoos lanterns, watercolor", "ليلة رمضانية، هلال فوق أسطح البيوت وفوانيس معلّقة، ألوان مائية"),
    Template("portrait", Kind.TEXT_TO_IMAGE, "realistic", "9:16", "📸", "Editorial portrait of a man in a traditional white thobe, desert background, golden light, 85mm", "صورة شخصية لرجل بثوب أبيض تقليدي، خلفية صحراوية وضوء ذهبي، عدسة 85 مم"),
    Template("portrait", Kind.TEXT_TO_IMAGE, "3d", "1:1", "🧸", "Cute 3D character avatar with big eyes and a hoodie, pastel background, Pixar style", "شخصية ثلاثية الأبعاد لطيفة بعيون كبيرة وهودي، خلفية باستيل، بأسلوب بيكسار"),
    Template("fantasy", Kind.TEXT_TO_VIDEO, "fantasy", "16:9", "🏰", "Floating castle above a waterfall, glowing crystals, camera flying towards it", "قلعة عائمة فوق شلال مع بلورات متوهجة، والكاميرا تطير نحوها"),
    Template("fantasy", Kind.TEXT_TO_IMAGE, "fantasy", "9:16", "🧞", "A genie rising from a golden lamp in a starry night bazaar, magical smoke", "جنّي يخرج من مصباح ذهبي في سوق ليلي تحت النجوم مع دخان سحري"),
)

private val CATS = listOf("all", "ads", "nature", "heritage", "cinema", "anime", "portrait", "fantasy")

private fun catLabel(c: String) = when (c) {
    "ads" -> R.string.cat_ads
    "nature" -> R.string.cat_nature
    "anime" -> R.string.cat_anime
    "cinema" -> R.string.cat_cinema
    "heritage" -> R.string.cat_heritage
    "portrait" -> R.string.cat_portrait
    "fantasy" -> R.string.cat_fantasy
    else -> R.string.cat_all
}

private fun catBrush(c: String): Brush = Brush.linearGradient(
    when (c) {
        "ads" -> listOf(Color(0xFFFF8A65), Color(0xFFF472B6))
        "nature" -> listOf(Color(0xFF10B981), Color(0xFF22D3EE))
        "anime" -> listOf(Color(0xFFF472B6), Color(0xFF7C5CFF))
        "cinema" -> listOf(Color(0xFF1E1B4B), Color(0xFF7C5CFF))
        "heritage" -> listOf(Color(0xFFD97706), Color(0xFF7C2D12))
        "portrait" -> listOf(Color(0xFF6366F1), Color(0xFF22D3EE))
        else -> listOf(Color(0xFF7C5CFF), Color(0xFF0E7490))
    },
)

@Composable
fun ExploreScreen(vm: AppViewModel, onUse: () -> Unit) {
    var cat by rememberSaveable { mutableStateOf("all") }
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val shown = if (cat == "all") TEMPLATES else TEMPLATES.filter { it.cat == cat }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.explore_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                ChipRow(CATS, cat, { stringResource(catLabel(it)) }) { cat = it }
            }
        }
        items(shown, key = { it.en }) { t ->
            val text = if (arabic) t.ar else t.en
            val tall = t.aspect == "9:16"
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { vm.useTemplate(t.kind, text, t.style, t.aspect); onUse() },
            ) {
                Box(
                    Modifier.fillMaxWidth().height(if (tall) 170.dp else 110.dp).background(catBrush(t.cat)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.emoji, fontSize = 44.sp)
                    Row(
                        Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (t.kind.isVideo) Icons.Outlined.Movie else Icons.Outlined.Image, null, Modifier.height(14.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text(t.aspect, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(Modifier.padding(12.dp)) {
                    Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.use_template) + " →",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
