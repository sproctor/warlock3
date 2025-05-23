package warlockfe.warlock3.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val Login: ImageVector
	get() {
		if (_Login != null) {
			return _Login!!
		}
		_Login = ImageVector.Builder(
            name = "Login",
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
				moveTo(480f, 840f)
				verticalLineToRelative(-80f)
				horizontalLineToRelative(280f)
				verticalLineToRelative(-560f)
				horizontalLineTo(480f)
				verticalLineToRelative(-80f)
				horizontalLineToRelative(280f)
				quadToRelative(33f, 0f, 56.5f, 23.5f)
				reflectiveQuadTo(840f, 200f)
				verticalLineToRelative(560f)
				quadToRelative(0f, 33f, -23.5f, 56.5f)
				reflectiveQuadTo(760f, 840f)
				close()
				moveToRelative(-80f, -160f)
				lineToRelative(-55f, -58f)
				lineToRelative(102f, -102f)
				horizontalLineTo(120f)
				verticalLineToRelative(-80f)
				horizontalLineToRelative(327f)
				lineTo(345f, 338f)
				lineToRelative(55f, -58f)
				lineToRelative(200f, 200f)
				close()
			}
		}.build()
		return _Login!!
	}

private var _Login: ImageVector? = null
