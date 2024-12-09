package warlockfe.warlock3.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val Palette: ImageVector
	get() {
		if (_Palette != null) {
			return _Palette!!
		}
		_Palette = ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
			path(
    			fill = SolidColor(Color.Black),
    			fillAlpha = 1.0f,
    			stroke = null,
    			strokeAlpha = 1.0f,
    			strokeLineWidth = 1.0f,
    			strokeLineCap = StrokeCap.Butt,
    			strokeLineJoin = StrokeJoin.Miter,
    			strokeLineMiter = 1.0f,
    			pathFillType = PathFillType.NonZero
			) {
				moveTo(480f, 880f)
				quadToRelative(-82f, 0f, -155f, -31.5f)
				reflectiveQuadToRelative(-127.5f, -86f)
				reflectiveQuadToRelative(-86f, -127.5f)
				reflectiveQuadTo(80f, 480f)
				quadToRelative(0f, -83f, 32.5f, -156f)
				reflectiveQuadToRelative(88f, -127f)
				reflectiveQuadTo(330f, 111.5f)
				reflectiveQuadTo(488f, 80f)
				quadToRelative(80f, 0f, 151f, 27.5f)
				reflectiveQuadToRelative(124.5f, 76f)
				reflectiveQuadToRelative(85f, 115f)
				reflectiveQuadTo(880f, 442f)
				quadToRelative(0f, 115f, -70f, 176.5f)
				reflectiveQuadTo(640f, 680f)
				horizontalLineToRelative(-74f)
				quadToRelative(-9f, 0f, -12.5f, 5f)
				reflectiveQuadToRelative(-3.5f, 11f)
				quadToRelative(0f, 12f, 15f, 34.5f)
				reflectiveQuadToRelative(15f, 51.5f)
				quadToRelative(0f, 50f, -27.5f, 74f)
				reflectiveQuadTo(480f, 880f)
				moveTo(260f, 520f)
				quadToRelative(26f, 0f, 43f, -17f)
				reflectiveQuadToRelative(17f, -43f)
				reflectiveQuadToRelative(-17f, -43f)
				reflectiveQuadToRelative(-43f, -17f)
				reflectiveQuadToRelative(-43f, 17f)
				reflectiveQuadToRelative(-17f, 43f)
				reflectiveQuadToRelative(17f, 43f)
				reflectiveQuadToRelative(43f, 17f)
				moveToRelative(120f, -160f)
				quadToRelative(26f, 0f, 43f, -17f)
				reflectiveQuadToRelative(17f, -43f)
				reflectiveQuadToRelative(-17f, -43f)
				reflectiveQuadToRelative(-43f, -17f)
				reflectiveQuadToRelative(-43f, 17f)
				reflectiveQuadToRelative(-17f, 43f)
				reflectiveQuadToRelative(17f, 43f)
				reflectiveQuadToRelative(43f, 17f)
				moveToRelative(200f, 0f)
				quadToRelative(26f, 0f, 43f, -17f)
				reflectiveQuadToRelative(17f, -43f)
				reflectiveQuadToRelative(-17f, -43f)
				reflectiveQuadToRelative(-43f, -17f)
				reflectiveQuadToRelative(-43f, 17f)
				reflectiveQuadToRelative(-17f, 43f)
				reflectiveQuadToRelative(17f, 43f)
				reflectiveQuadToRelative(43f, 17f)
				moveToRelative(120f, 160f)
				quadToRelative(26f, 0f, 43f, -17f)
				reflectiveQuadToRelative(17f, -43f)
				reflectiveQuadToRelative(-17f, -43f)
				reflectiveQuadToRelative(-43f, -17f)
				reflectiveQuadToRelative(-43f, 17f)
				reflectiveQuadToRelative(-17f, 43f)
				reflectiveQuadToRelative(17f, 43f)
				reflectiveQuadToRelative(43f, 17f)
				moveTo(480f, 800f)
				quadToRelative(9f, 0f, 14.5f, -5f)
				reflectiveQuadToRelative(5.5f, -13f)
				quadToRelative(0f, -14f, -15f, -33f)
				reflectiveQuadToRelative(-15f, -57f)
				quadToRelative(0f, -42f, 29f, -67f)
				reflectiveQuadToRelative(71f, -25f)
				horizontalLineToRelative(70f)
				quadToRelative(66f, 0f, 113f, -38.5f)
				reflectiveQuadTo(800f, 442f)
				quadToRelative(0f, -121f, -92.5f, -201.5f)
				reflectiveQuadTo(488f, 160f)
				quadToRelative(-136f, 0f, -232f, 93f)
				reflectiveQuadToRelative(-96f, 227f)
				quadToRelative(0f, 133f, 93.5f, 226.5f)
				reflectiveQuadTo(480f, 800f)
			}
		}.build()
		return _Palette!!
	}

private var _Palette: ImageVector? = null
