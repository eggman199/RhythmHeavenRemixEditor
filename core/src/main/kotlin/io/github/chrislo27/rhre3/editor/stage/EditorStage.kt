package io.github.chrislo27.rhre3.editor.stage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import io.github.chrislo27.rhre3.PreferenceKeys
import io.github.chrislo27.rhre3.RHRE3
import io.github.chrislo27.rhre3.RHRE3Application
import io.github.chrislo27.rhre3.editor.ClickOccupation
import io.github.chrislo27.rhre3.editor.Editor
import io.github.chrislo27.rhre3.editor.Tool
import io.github.chrislo27.rhre3.entity.model.special.SubtitleEntity
import io.github.chrislo27.rhre3.registry.Game
import io.github.chrislo27.rhre3.registry.GameGroupListComparatorIgnorePriority
import io.github.chrislo27.rhre3.registry.GameRegistry
import io.github.chrislo27.rhre3.registry.Series
import io.github.chrislo27.rhre3.registry.datamodel.impl.Cue
import io.github.chrislo27.rhre3.screen.EditorScreen
import io.github.chrislo27.rhre3.track.PlayState
import io.github.chrislo27.toolboks.Toolboks
import io.github.chrislo27.toolboks.i18n.Localization
import io.github.chrislo27.toolboks.registry.AssetRegistry
import io.github.chrislo27.toolboks.registry.ScreenRegistry
import io.github.chrislo27.toolboks.ui.*
import io.github.chrislo27.toolboks.util.gdxutils.getInputX
import io.github.chrislo27.toolboks.util.gdxutils.getInputY
import io.github.chrislo27.toolboks.util.gdxutils.getTextHeight
import io.github.chrislo27.toolboks.util.gdxutils.getTextWidth
import java.util.*


class EditorStage(parent: UIElement<EditorScreen>?,
                  camera: OrthographicCamera, val main: RHRE3Application, val editor: Editor)
    : Stage<EditorScreen>(parent, camera), Palettable {

    override var palette: UIPalette = main.uiPalette.copy(
            backColor = Color(main.uiPalette.backColor).apply { this.a = 0.5f })
    val paneLikeStages: List<Stage<EditorScreen>> = mutableListOf()
    val messageBarStage: Stage<EditorScreen>
    val buttonBarStage: Stage<EditorScreen>
    val pickerStage: Stage<EditorScreen>
    val pickerDisplay: PickerDisplay
    val minimapBarStage: Stage<EditorScreen>
    val centreAreaStage: Stage<EditorScreen>
    val subtitleStage: Stage<EditorScreen>
    val patternAreaStage: Stage<EditorScreen>
    val tapalongStage: TapalongStage
    val presentationModeStage: PresentationModeStage
    val themeEditorStage: ThemeEditorStage
    val themeChooserStage: ThemeChooserStage
    val viewChooserStage: ViewChooserStage

    val gameButtons: List<GameButton>
    val variantButtons: List<GameButton>
    val seriesButtons: List<SeriesButton>
    val toolButtons: List<ToolButton>
    val gameScrollButtons: List<Button<EditorScreen>>
    val variantScrollButtons: List<Button<EditorScreen>>

    val selectorRegion: TextureRegion by lazy { TextureRegion(AssetRegistry.get<Texture>("ui_selector_fever")) }
    val selectorRegionSeries: TextureRegion by lazy { TextureRegion(AssetRegistry.get<Texture>("ui_selector")) }
    lateinit var searchBar: SearchBar<EditorScreen>
        private set
    val messageLabel: TextLabel<EditorScreen>

    val hoverTextLabel: TextLabel<EditorScreen>

    lateinit var playButton: PlaybackButton
        private set
    lateinit var pauseButton: PlaybackButton
        private set
    lateinit var stopButton: PlaybackButton
        private set
    lateinit var langButton: LangButton
        private set
    lateinit var jumpToField: JumpToField
        private set
    lateinit var subtitleLabel: TextLabel<EditorScreen>
        private set
    lateinit var subtitleField: TextField<EditorScreen>
        private set

    val topOfMinimapBar: Float
        get() {
            return centreAreaStage.location.realY
        }
    val isTyping: Boolean
        get() {
            return searchBar.textField.hasFocus || jumpToField.hasFocus || subtitleField.hasFocus
        }
    val tapalongMarkersEnabled: Boolean
        get() = tapalongStage.markersEnabled

    private var isDirty = DirtyType.CLEAN
    private var wasDebug = false

    enum class DirtyType {
        CLEAN, DIRTY, SEARCH_DIRTY
    }

    interface HasHoverText {
        fun getHoverText(): String
    }

    private fun setHoverText(text: String) {
        val label = hoverTextLabel
        val labelLoc = label.location
        val font = label.getFont()

        label.visible = true
        label.text = text

        font.data.setScale(label.palette.fontScale * label.fontScaleMultiplier)

        labelLoc.set(pixelX = camera.getInputX(), pixelY = camera.getInputY() + 2,
                     pixelWidth = font.getTextWidth(label.text) + 6,
                     pixelHeight = font.getTextHeight(text) + font.capHeight)

        val yLimit = label.stage.camera.viewportHeight
        val top = labelLoc.pixelY + labelLoc.pixelHeight
        if (top > yLimit) {
            val height = labelLoc.pixelHeight
            labelLoc.set(pixelY = yLimit - labelLoc.pixelHeight,
                         pixelX = labelLoc.pixelX + ((top - yLimit) / height).coerceAtMost(1f) * height)
        }

        // clamp X
        labelLoc.set(pixelX = Math.min(labelLoc.pixelX,
                                       label.stage.camera.viewportWidth - labelLoc.pixelWidth))

        font.data.setScale(1f)
        val labelParent = label.parent!!
        label.onResize(labelParent.location.realWidth, labelParent.location.realHeight)
    }

    override fun render(screen: EditorScreen, batch: SpriteBatch, shapeRenderer: ShapeRenderer) {
        hoverTextLabel.visible = false
        elements.firstOrNull {
            if (it is HasHoverText && it.isMouseOver() && it.visible) {
                setHoverText(it.getHoverText())
                true
            } else false
        } ?: elements.firstOrNull { stage ->
            if (stage !is Stage || !stage.visible) {
                false
            } else {
                stage.elements.any {
                    if (it is HasHoverText && it.isMouseOver() && it.visible) {
                        setHoverText(it.getHoverText())
                        true
                    } else false
                }
            }
        } ?: searchBar.elements.firstOrNull {
            // hack
            if (it is HasHoverText && it.isMouseOver() && it.visible) {
                setHoverText(it.getHoverText())
                true
            } else false
        }

        if (Toolboks.debugMode != wasDebug) {
            wasDebug = Toolboks.debugMode
            updateSelected()
        }

        super.render(screen, batch, shapeRenderer)

        if (isDirty != DirtyType.CLEAN && !GameRegistry.isDataLoading()) {
            val selection = editor.pickerSelection.currentSelection
            val series = editor.pickerSelection.currentSeries
            val isSearching = editor.pickerSelection.isSearching

            if (isSearching) {
                if (isDirty == DirtyType.SEARCH_DIRTY) {
                    selection.groups.clear()
                    selection.variants.clear()
                    selection.group = 0
                    val query = searchBar.textField.text.toLowerCase(Locale.ROOT)
                    searchBar.filterGameGroups(query).mapTo(selection.groups) { it }

                    selection.groups.sortWith(compareBy(GameGroupListComparatorIgnorePriority) { it.games.first() })
                }
            } else {
                selection.groups.clear()
                GameRegistry.data.gameGroupsList
                        .filter { it.series == series }
                        .mapTo(selection.groups) { it }

                selection.groups.sortBy { it.games.first() }
            }

            seriesButtons.forEach {
                it.selected = series == it.series && !isSearching
            }
            toolButtons.forEach {
                it.selected = it.tool == editor.currentTool
            }

            gameButtons.forEach {
                it.game = null
            }
            selection.groups
                    .forEachIndexed { index, it ->
                        val x: Int = index % Editor.ICON_COUNT_X
                        val y: Int = index / Editor.ICON_COUNT_X - selection.groupScroll

                        if (y in 0 until Editor.ICON_COUNT_Y) {
                            val buttonIndex = y * Editor.ICON_COUNT_X + x
                            gameButtons[buttonIndex].apply {
                                this.game = it.games[selection.getVariant(index)?.variant ?: return@apply]
                                if (selection.group == index) {
                                    this.selected = true
                                }
                            }
                        }
                    }

            variantButtons.forEach {
                it.game = null
            }
            selection.groups.getOrNull(selection.group)?.also { group ->
                group.games.forEachIndexed { index, game ->
                    val y = index - (selection.getCurrentVariant()?.variantScroll ?: return@forEachIndexed)
                    if (y in 0 until Editor.ICON_COUNT_Y) {
                        variantButtons[y].apply {
                            this.game = game
                            if (selection.getCurrentVariant()!!.variant == index) {
                                this.selected = true
                            }
                        }
                    }
                }
            }

            if (selection.groups.isNotEmpty() && selection.getCurrentVariant()!!.placeableObjects.isNotEmpty()) {
                val variant = selection.getCurrentVariant()
                val objects = variant!!.placeableObjects

                objects.forEachIndexed { index, datamodel ->
                    var text = datamodel.name
                    var color = Color.WHITE
                    val label: PickerDisplay.Label = pickerDisplay.labels.getOrNull(index) ?: run {
                        val l = PickerDisplay.Label("", Color.WHITE)
                        pickerDisplay.labels.add(l)
                        l
                    }
                    if (Toolboks.debugMode) {
                        text += " [GRAY](${datamodel.id})[]"
                    }
                    if (index != variant.pattern && datamodel is Cue) {
                        color = Editor.CUE_PATTERN_COLOR
                    }
                    label.string = text
                    label.color = color
                }
                while (pickerDisplay.labels.size > objects.size) {
                    pickerDisplay.labels.removeAt(pickerDisplay.labels.size - 1)
                }
            }

            editor.updateMessageLabel()

            isDirty = DirtyType.CLEAN
        }
    }

    fun updateSelected(type: DirtyType = DirtyType.DIRTY) {
        isDirty = type
    }

    init {
        paneLikeStages as MutableList<Stage<EditorScreen>>
        gameButtons = mutableListOf()
        variantButtons = mutableListOf()
        seriesButtons = mutableListOf()
        toolButtons = mutableListOf()
        gameScrollButtons = mutableListOf()
        variantScrollButtons = mutableListOf()

        messageBarStage = Stage(this, camera).apply {
            this.location.set(0f, 0f,
                              1f, Editor.MESSAGE_BAR_HEIGHT / RHRE3.HEIGHT.toFloat())

            this.updatePositions()
            this.elements +=
                    ColourPane(this, this).apply {
                        this.colour.set(Editor.TRANSLUCENT_BLACK)
                        this.colour.a = 0.75f
                    }
        }
        messageLabel = object : TextLabel<EditorScreen>(palette,
                                                        messageBarStage, messageBarStage) {
            private var lastVersionTextWidth: Float = -1f

            override fun render(screen: EditorScreen, batch: SpriteBatch, shapeRenderer: ShapeRenderer) {
                super.render(screen, batch, shapeRenderer)
                if (main.versionTextWidth != lastVersionTextWidth) {
                    lastVersionTextWidth = main.versionTextWidth
                    this.location.set(screenX = messageBarStage.percentageOfWidth(2f),
                                      screenY = messageBarStage.percentageOfHeight(2f))
                    this.location.set(
                            screenWidth = 1f - (main.versionTextWidth / messageBarStage.location.realWidth + this.location.screenX))
                    this.stage.updatePositions()
                }
            }
        }.apply {
            this.fontScaleMultiplier = 0.5f
            this.textAlign = Align.bottomLeft
            this.textWrapping = false
            this.location.set(0f, 0f,
                              1f,
                              1.5f,
                              pixelWidth = -8f)
            this.isLocalizationKey = false
        }
        messageBarStage.elements += messageLabel
        elements += messageBarStage
        buttonBarStage = Stage(this, camera).apply {
            this.location.set(screenX = (Editor.BUTTON_PADDING / RHRE3.WIDTH),
                              screenY = 1f - ((Editor.BUTTON_PADDING + Editor.BUTTON_SIZE) / RHRE3.HEIGHT),
                              screenWidth = 1f - (Editor.BUTTON_PADDING / RHRE3.WIDTH) * 2f,
                              screenHeight = Editor.BUTTON_SIZE / RHRE3.HEIGHT)
        }
        pickerStage = object : Stage<EditorScreen>(this, camera) {
            override fun scrolled(amount: Int): Boolean {
                if (isMouseOver()) {
                    val selection = editor.pickerSelection.currentSelection
                    val currentVariant = selection.getCurrentVariant() ?: return false
                    when (stage.camera.getInputX()) {
                        in (location.realX + location.realWidth * 0.5f)..(location.realX + location.realWidth) -> {
                            val old = currentVariant.pattern
                            currentVariant.pattern =
                                    (currentVariant.pattern + amount)
                                            .coerceIn(0, currentVariant.maxPatternScroll)
                            if (old != currentVariant.pattern) {
                                updateSelected()
                                return true
                            }
                        }
                        in (variantButtons.first().location.realX)..(location.realX + location.realWidth * 0.5f) -> {
                            val old = currentVariant.variantScroll
                            currentVariant.variantScroll =
                                    (currentVariant.variantScroll + amount)
                                            .coerceIn(0, currentVariant.maxScroll)
                            if (old != currentVariant.variantScroll) {
                                updateSelected()
                                return true
                            }
                        }
                        in (location.realX)..(variantButtons.first().location.realX) -> {
                            val old = selection.groupScroll
                            selection.groupScroll =
                                    (selection.groupScroll + amount)
                                            .coerceIn(0, selection.maxGroupScroll)
                            if (old != selection.groupScroll) {
                                updateSelected()
                                return true
                            }
                        }
                    }
                }

                return false
            }
        }.apply {
            this.location.set(screenY = messageBarStage.location.screenY + messageBarStage.location.screenHeight,
                              screenHeight = ((Editor.ICON_SIZE + Editor.ICON_PADDING) * Editor.ICON_COUNT_Y + Editor.ICON_PADDING) / RHRE3.HEIGHT
                             )
            this.elements += ColourPane(this, this).apply {
                this.colour.set(Editor.TRANSLUCENT_BLACK)
            }
            this.elements += ColourPane(this, this).apply {
                this.colour.set(1f, 1f, 1f, 1f)
                this.location.set(screenX = 0.5f, screenWidth = 0f, screenHeight = 1f, pixelX = -0.5f, pixelWidth = 1f)
            }
        }
        patternAreaStage = Stage(this, camera).apply {
            this.location.set(screenY = pickerStage.location.screenY,
                              screenHeight = pickerStage.location.screenHeight,
                              screenX = 0.5f,
                              screenWidth = 0.5f)
        }
        minimapBarStage = Stage(this, camera).apply {
            this.location.set(screenY = pickerStage.location.screenY + pickerStage.location.screenHeight,
                              screenHeight = Editor.ICON_SIZE / RHRE3.HEIGHT)
            this.elements += ColourPane(this, this).apply {
                this.colour.set(Editor.TRANSLUCENT_BLACK)
            }
            this.elements += ColourPane(this, this).apply {
                this.colour.set(1f, 1f, 1f, 1f)
                this.location.set(screenX = 0f, screenWidth = 1f, screenHeight = 0f, pixelY = -1f, pixelHeight = 1f)
            }
        }

        centreAreaStage = Stage(this, camera).apply {
            this.location.set(screenY = minimapBarStage.location.screenY + minimapBarStage.location.screenHeight)
            this.location.set(
                    screenHeight = (buttonBarStage.location.screenY - this.location.screenY - (Editor.BUTTON_PADDING / RHRE3.HEIGHT)))
            this.updatePositions()
            this.elements += GameDisplayStage(editor, palette, this, this.camera).apply display@ {
                this.location.set(screenHeight = this@apply.percentageOfHeight(32f),
                                  screenX = this@apply.percentageOfWidth(8f),
                                  screenWidth = this@apply.percentageOfWidth(
                                          32f) * GameDisplayStage.WIDTH_MULTIPLICATION)
                this.location.set(screenY = 1f - (this@apply.percentageOfHeight(8f) + this.location.screenHeight))
                this.updatePositions()
            }
        }
        subtitleStage = Stage(this, camera).apply {
            this.location.set(centreAreaStage.location.screenX, centreAreaStage.location.screenY,
                              centreAreaStage.location.screenWidth, centreAreaStage.location.screenHeight)
            subtitleLabel = object : TextLabel<EditorScreen>(palette, this@apply, this@apply) {
                override fun getFont(): BitmapFont {
                    return main.defaultBorderedFont
                }

                override fun render(screen: EditorScreen, batch: SpriteBatch, shapeRenderer: ShapeRenderer) {
                    text = (if (main.preferences.getBoolean(PreferenceKeys.SETTINGS_SUBTITLE_ORDER, false))
                        editor.remix.currentSubtitles
                    else editor.remix.currentSubtitlesReversed).joinToString(separator = "\n") { it.subtitle }

                    super.render(screen, batch, shapeRenderer)
                }
            }.apply {
                this.location.set(screenY = 0.025f)
                this.location.set(screenHeight = 0.1f - this.location.screenY)
                this.textWrapping = false
                this.isLocalizationKey = false
                this.textAlign = Align.bottom or Align.center
            }
            this.elements += subtitleLabel
            // TODO maybe make generic text field for other entities in the future
            subtitleField = object : TextField<EditorScreen>(palette, this@apply, this@apply) {
                override fun frameUpdate(screen: EditorScreen) {
                    super.frameUpdate(screen)

                    if (visible &&
                            (!hasFocus
                                    || editor.selection.size != 1
                                    || editor.selection.first() !is SubtitleEntity)) {
                        visible = false
                    }
                }

                override fun onTextChange(oldText: String) {
                    super.onTextChange(oldText)

                    if (!hasFocus || !visible)
                        return

                    if (editor.selection.firstOrNull() is SubtitleEntity) {
                        val entity = editor.selection.first() as SubtitleEntity
                        entity.subtitle = this.text
                    }
                }
            }.apply {
                this.location.set(screenY = subtitleLabel.location.screenHeight,
                                  screenHeight = 0.1f,
                                  screenWidth = 0.5f,
                                  screenX = 0.25f)
                this.background = true
                this.visible = false
            }
            this.elements += subtitleField
        }
        hoverTextLabel = TextLabel(palette.copy(backColor = Color(palette.backColor).also { it.a *= 1.5f }),
                                   this, this).apply {
            this.background = true
            this.isLocalizationKey = false
            this.fontScaleMultiplier = 0.75f
            this.textWrapping = false
            this.visible = false
            this.alignment = Align.bottomLeft
            this.textAlign = Align.center
            this.location.set(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        tapalongStage = TapalongStage(editor, palette, this, camera).apply {
            this.location.set(0f,
                              messageBarStage.location.screenY + messageBarStage.location.screenHeight,
                              1f, pickerStage.location.screenHeight + minimapBarStage.location.screenHeight)

            this.visible = false
        }
        presentationModeStage = PresentationModeStage(editor, palette, this, camera).apply {
            this.location.set(0f,
                              messageBarStage.location.screenY + messageBarStage.location.screenHeight,
                              1f, pickerStage.location.screenHeight + minimapBarStage.location.screenHeight)

            this.visible = false
        }
        themeEditorStage = ThemeEditorStage(editor, palette, this, camera).apply {
            this.location.set(screenWidth = 0.3f,
                              screenY = minimapBarStage.location.screenY + minimapBarStage.location.screenHeight)
            this.location.set(screenX = 1f - this.location.screenWidth,
                              screenHeight = (buttonBarStage.location.screenY - this@EditorStage.percentageOfHeight(
                                      Editor.BUTTON_PADDING)) - (this.location.screenY))
            this.visible = false
        }
        themeChooserStage = ThemeChooserStage(editor, palette, this, camera).apply {
            this.location.set(screenWidth = 0.3f,
                              screenY = minimapBarStage.location.screenY + minimapBarStage.location.screenHeight)
            this.location.set(screenX = 1f - this.location.screenWidth,
                              screenHeight = (buttonBarStage.location.screenY - this@EditorStage.percentageOfHeight(
                                      Editor.BUTTON_PADDING)) - (this.location.screenY))
            this.visible = false
        }
        viewChooserStage = ViewChooserStage(editor, palette, this, camera).apply {
            this.location.set(screenWidth = 0.3f,
                              screenY = minimapBarStage.location.screenY + minimapBarStage.location.screenHeight)
            this.location.set(screenX = 1f - this.location.screenWidth,
                              screenHeight = (buttonBarStage.location.screenY - this@EditorStage.percentageOfHeight(
                                      Editor.BUTTON_PADDING)) - (this.location.screenY))
            this.visible = false
        }
        elements += presentationModeStage
        elements += tapalongStage
        elements += buttonBarStage
        elements += pickerStage
        elements += patternAreaStage
        elements += minimapBarStage
        elements += centreAreaStage
        elements += subtitleStage
//        elements += themeEditorStage
        elements += themeChooserStage
        elements += viewChooserStage
        elements += hoverTextLabel
        paneLikeStages += themeChooserStage
        paneLikeStages += viewChooserStage
        paneLikeStages += themeEditorStage
        this.updatePositions()

        pickerDisplay = PickerDisplay(editor, Editor.PATTERN_COUNT, palette, patternAreaStage, patternAreaStage)

        run pickerAndCo@ {
            val iconWidth = pickerStage.percentageOfWidth(Editor.ICON_SIZE)
            val iconHeight = pickerStage.percentageOfHeight(Editor.ICON_SIZE)
            val iconWidthPadded = pickerStage.percentageOfWidth(
                    Editor.ICON_SIZE + Editor.ICON_PADDING)
            val iconHeightPadded = pickerStage.percentageOfHeight(
                    Editor.ICON_SIZE + Editor.ICON_PADDING)
            val startX = pickerStage.percentageOfWidth(
                    (pickerStage.location.realWidth / 2f) -
                            ((Editor.ICON_SIZE + Editor.ICON_PADDING) * (Editor.ICON_COUNT_X + 3)
                                    - Editor.ICON_PADDING)
                                                      ) / 2f
            val startY = 1f - (pickerStage.percentageOfHeight(
                    (Editor.ICON_SIZE + Editor.ICON_PADDING) * (Editor.ICON_COUNT_Y - 2) / 2f
                                                             ))

            // Picker area
            run picker@ {
                pickerStage.updatePositions()
                gameButtons as MutableList
                variantButtons as MutableList

                fun UIElement<*>.setLocation(x: Int, y: Int) {
                    this.location.set(
                            screenX = startX + iconWidthPadded * x,
                            screenY = startY - iconHeightPadded * y,
                            screenWidth = iconWidth,
                            screenHeight = iconHeight
                                     )
                }


                for (y in 0 until Editor.ICON_COUNT_Y) {
                    for (x in 0 until Editor.ICON_COUNT_X + 3) {
                        if (x == Editor.ICON_COUNT_X || x == Editor.ICON_COUNT_X + 2) {
                            if (y != 0 && y != Editor.ICON_COUNT_Y - 1)
                                continue
                            val isUp: Boolean = y == 0
                            val isVariant: Boolean = x == Editor.ICON_COUNT_X + 2
                            val button = object : Button<EditorScreen>(palette, pickerStage, pickerStage) {
                                override fun render(screen: EditorScreen, batch: SpriteBatch,
                                                    shapeRenderer: ShapeRenderer) {
                                    super.render(screen, batch, shapeRenderer)
                                    val selection = editor.pickerSelection.currentSelection
                                    val label = this.labels.first() as TextLabel
                                    if (GameRegistry.isDataLoading() || editor.pickerSelection.currentSelection.groups.isEmpty()) {
                                        if (isUp) {
                                            label.text = Editor.ARROWS[2]
                                        } else {
                                            label.text = Editor.ARROWS[3]
                                        }
                                    } else {
                                        if (isVariant) {
                                            val current = selection.getCurrentVariant()
                                            if (isUp) {
                                                if (current?.variantScroll ?: 0 > 0) {
                                                    label.text = Editor.ARROWS[0]
                                                } else {
                                                    label.text = Editor.ARROWS[2]
                                                }
                                            } else {
                                                if (current?.variantScroll ?: 0 < current?.maxScroll ?: 0) {
                                                    label.text = Editor.ARROWS[1]
                                                } else {
                                                    label.text = Editor.ARROWS[3]
                                                }
                                            }
                                        } else {
                                            if (isUp) {
                                                if (selection.groupScroll > 0) {
                                                    label.text = Editor.ARROWS[0]
                                                } else {
                                                    label.text = Editor.ARROWS[2]
                                                }
                                            } else {
                                                if (selection.groupScroll < selection.maxGroupScroll) {
                                                    label.text = Editor.ARROWS[1]
                                                } else {
                                                    label.text = Editor.ARROWS[3]
                                                }
                                            }
                                        }
                                    }
                                }

                                override fun onLeftClick(xPercent: Float, yPercent: Float) {
                                    super.onLeftClick(xPercent, yPercent)
                                    val selection = editor.pickerSelection.currentSelection
                                    if (isVariant) {
                                        val current = selection.getCurrentVariant() ?: return
                                        if (isUp) {
                                            if (current.variantScroll > 0) {
                                                current.variantScroll--
                                                updateSelected()
                                            }
                                        } else {
                                            if (current.variantScroll < current.maxScroll) {
                                                current.variantScroll++
                                                updateSelected()
                                            }
                                        }
                                    } else {
                                        if (isUp) {
                                            if (selection.groupScroll > 0) {
                                                selection.groupScroll--
                                                updateSelected()
                                            }
                                        } else {
                                            if (selection.groupScroll < selection.maxGroupScroll) {
                                                selection.groupScroll++
                                                updateSelected()
                                            }
                                        }
                                    }
                                }
                            }.apply {
                                this.setLocation(x, y)
                                this.background = false
                                this.addLabel(
                                        object : TextLabel<EditorScreen>(palette, this, this.stage) {
                                            override fun getFont(): BitmapFont {
                                                return main.defaultBorderedFont
                                            }
                                        }.apply {
                                            this.setText(
                                                    if (isUp) Editor.ARROWS[2] else Editor.ARROWS[3],
                                                    Align.center, false, false
                                                        )
                                            this.background = false
                                        })
                            }

                            pickerStage.elements += button
                            if (isVariant) {
                                (variantScrollButtons as MutableList) += button
                            } else {
                                (gameScrollButtons as MutableList) += button
                            }
                        } else {
                            val isVariant = x == Editor.ICON_COUNT_X + 1
                            val button = GameButton(x, y, palette, pickerStage, pickerStage, { _, _ ->
                                this as GameButton
                                if (visible && this.game != null) {
                                    val selection = editor.pickerSelection.currentSelection
                                    if (isVariant) {
                                        selection.getVariant(selection.group)?.variant =
                                                y + selection.getVariant(selection.group)!!.variantScroll
                                    } else {
                                        selection.group =
                                                (y + editor.pickerSelection.currentSelection.groupScroll) * Editor.ICON_COUNT_X + x
                                    }
                                    updateSelected()
                                }
                            }).apply {
                                this.setLocation(x, y)
                            }
                            if (isVariant) {
                                variantButtons += button
                            } else {
                                gameButtons += button
                            }
                        }
                    }
                }
                pickerStage.elements.addAll(gameButtons)
                pickerStage.elements.addAll(variantButtons)
            }

            run patternArea@ {
                val borderedPalette = palette.copy(ftfont = main.fonts[main.defaultBorderedFontKey])
                val padding2 = pickerStage.percentageOfWidth(
                        Editor.ICON_PADDING * 2)

                val upButton = object : Button<EditorScreen>(borderedPalette, patternAreaStage, patternAreaStage) {
                    override fun render(screen: EditorScreen, batch: SpriteBatch,
                                        shapeRenderer: ShapeRenderer) {
                        super.render(screen, batch, shapeRenderer)
                        val selection = editor.pickerSelection.currentSelection
                        val label = this.labels.first() as TextLabel
                        if (GameRegistry.isDataLoading() || editor.pickerSelection.currentSelection.groups.isEmpty()) {
                            label.text = Editor.ARROWS[2]
                        } else {
                            val current = selection.getCurrentVariant()
                            if (current?.pattern ?: 0 > 0) {
                                label.text = Editor.ARROWS[0]
                            } else {
                                label.text = Editor.ARROWS[2]
                            }
                        }
                    }

                    override fun onLeftClick(xPercent: Float, yPercent: Float) {
                        super.onLeftClick(xPercent, yPercent)
                        val selection = editor.pickerSelection.currentSelection
                        val current = selection.getCurrentVariant() ?: return
                        if (current.pattern > 0) {
                            current.pattern--
                            updateSelected()
                        }
                    }
                }.apply {
                    this.location.set(screenX = padding2,
                                      screenWidth = patternAreaStage.percentageOfWidth(
                                              Editor.ICON_SIZE),
                                      screenHeight = patternAreaStage.percentageOfHeight(
                                              Editor.ICON_SIZE),
                                      screenY = startY)
                    this.background = false
                    this.addLabel(
                            TextLabel(borderedPalette, this, this.stage).apply {
                                this.setText(
                                        Editor.ARROWS[2],
                                        Align.center, false, false
                                            )
                                this.background = false
                            })
                }
                val downButton = object : Button<EditorScreen>(borderedPalette, patternAreaStage, patternAreaStage) {
                    override fun render(screen: EditorScreen, batch: SpriteBatch,
                                        shapeRenderer: ShapeRenderer) {
                        super.render(screen, batch, shapeRenderer)
                        val selection = editor.pickerSelection.currentSelection
                        val label = this.labels.first() as TextLabel
                        if (GameRegistry.isDataLoading() || editor.pickerSelection.currentSelection.groups.isEmpty()) {
                            label.text = Editor.ARROWS[3]
                        } else {
                            val current = selection.getCurrentVariant() ?: return
                            if (current.pattern < current.maxPatternScroll) {
                                label.text = Editor.ARROWS[1]
                            } else {
                                label.text = Editor.ARROWS[3]
                            }
                        }
                    }

                    override fun onLeftClick(xPercent: Float, yPercent: Float) {
                        super.onLeftClick(xPercent, yPercent)
                        val selection = editor.pickerSelection.currentSelection
                        val current = selection.getCurrentVariant() ?: return
                        if (current.pattern < current.maxPatternScroll) {
                            current.pattern++
                            updateSelected()
                        }
                    }
                }.apply {
                    this.location.set(screenX = padding2,
                                      screenWidth = patternAreaStage.percentageOfWidth(
                                              Editor.ICON_SIZE),
                                      screenHeight = patternAreaStage.percentageOfHeight(
                                              Editor.ICON_SIZE),
                                      screenY = startY - iconHeightPadded * (Editor.ICON_COUNT_Y - 1))
                    this.background = false
                    this.addLabel(
                            TextLabel(borderedPalette, this, this.stage).apply {
                                this.setText(
                                        Editor.ARROWS[3],
                                        Align.center, false, false
                                            )
                                this.background = false
                            })
                }

                patternAreaStage.elements += upButton
                patternAreaStage.elements += downButton

                val labelCount = Editor.PATTERN_COUNT
                val height = 1f / labelCount

                patternAreaStage.elements +=
                        TextLabel(borderedPalette.copy(textColor = Color(Editor.SELECTED_TINT)),
                                  patternAreaStage, patternAreaStage).apply {
                            this.location.set(
                                    screenX = padding2,
                                    screenWidth = patternAreaStage.percentageOfWidth(
                                            Editor.ICON_SIZE),
                                    screenHeight = height,
                                    screenY = 1f - (height * (1 + (labelCount / 2)))
                                             )
                            this.isLocalizationKey = false
                            this.textAlign = Align.center
                            this.textWrapping = false
                            this.text = Editor.ARROWS[4]
                        }

                patternAreaStage.elements += pickerDisplay.apply {
                    this.location.set(
                            screenHeight = 1f,
                            screenY = 0f,
                            screenX = upButton.location.screenX + upButton.location.screenWidth +
                                    padding2
                                     )
                    this.location.set(screenWidth = 1f - this.location.screenX)
                }

            }
        }

        // Minimap area
        run minimap@ {
            minimapBarStage.updatePositions()
            seriesButtons as MutableList

            val buttonWidth: Float = minimapBarStage.percentageOfWidth(
                    Editor.ICON_SIZE)
            val buttonHeight: Float = 1f

            Series.VALUES.forEachIndexed { index, series ->
                val tmp = SeriesButton(series, palette, minimapBarStage, minimapBarStage, { x, y ->
                    editor.pickerSelection.currentSeries = series
                    updateSelected()
                }).apply {
                    this.location.set(
                            screenWidth = buttonWidth,
                            screenHeight = buttonHeight,
                            screenX = index * buttonWidth
                                     )
                    this.label.image = TextureRegion(AssetRegistry.get<Texture>(series.textureId))
                }
                seriesButtons += tmp

            }
            minimapBarStage.elements.addAll(seriesButtons)

            val lastSeriesButton = seriesButtons.last()
            val searchBarX = lastSeriesButton.location.screenX + lastSeriesButton.location.screenWidth
            val searchBarWidth = 0.5f - searchBarX
            minimapBarStage.updatePositions()
            searchBar = SearchBar(searchBarWidth,
                                  editor, this, palette, minimapBarStage, camera).apply {
                this.location.set(screenX = searchBarX,
                                  screenHeight = 1f)
                this.location.set(screenWidth = searchBarWidth)
            }
            minimapBarStage.elements += searchBar

            toolButtons as MutableList
            Tool.VALUES.forEachIndexed { index, tool ->
                toolButtons += ToolButton(tool, palette, minimapBarStage, minimapBarStage,
                                          { x, y ->
                                              if (editor.clickOccupation == ClickOccupation.None) {
                                                  editor.currentTool = tool
                                                  updateSelected()
                                              }
                                          }).apply {
                    this.location.set(
                            screenWidth = buttonWidth,
                            screenHeight = buttonHeight,
                            screenX = 1f - Tool.VALUES.size * buttonWidth + index * buttonWidth
                                     )
                    this.background = true
                }
            }
            minimapBarStage.elements.addAll(toolButtons)
            minimapBarStage.elements += Minimap(editor, palette, minimapBarStage, minimapBarStage).apply {
                this.location.set(screenX = 0.5f, screenWidth = 0.5f - Tool.VALUES.size * buttonWidth)
            }
        }

        // Button bar
        run buttonBar@ {
            buttonBarStage.updatePositions()
            val stageWidth = buttonBarStage.location.realWidth
            val stageHeight = buttonBarStage.location.realHeight
            val padding = Editor.BUTTON_PADDING / stageWidth
            val size = Editor.BUTTON_SIZE / stageWidth

            buttonBarStage.elements +=
                    ColourPane(buttonBarStage, buttonBarStage).apply {
                        this.colour.set(Editor.TRANSLUCENT_BLACK)
                        this.colour.a = 0.5f
                        this.location.set(
                                screenX = -(Editor.BUTTON_PADDING / stageWidth),
                                screenY = -(Editor.BUTTON_PADDING / stageHeight),
                                screenWidth = 1f + (Editor.BUTTON_PADDING / stageWidth) * 2f,
                                screenHeight = 1f + (Editor.BUTTON_PADDING / stageHeight) * 2f
                                         )
                    }

            playButton = PlaybackButton(PlayState.PLAYING, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size, screenHeight = 1f)
                this.location.set(screenX = 0.5f - size / 2)
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_play"))
                    this.tint = Color(0f, 0.5f, 0.055f, 1f)
                })
            }
            pauseButton = PlaybackButton(PlayState.PAUSED, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size, screenHeight = 1f)
                this.location.set(screenX = 0.5f - size / 2 - size - padding)
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_pause"))
                    this.tint = Color(0.75f, 0.75f, 0.25f, 1f)
                })
            }
            stopButton = PlaybackButton(PlayState.STOPPED, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size, screenHeight = 1f)
                this.location.set(screenX = 0.5f - size / 2 + size + padding)
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_stop"))
                    this.tint = Color(242 / 255f, 0.0525f, 0.0525f, 1f)
                })
            }
            buttonBarStage.elements += playButton
            buttonBarStage.elements += pauseButton
            buttonBarStage.elements += stopButton

            buttonBarStage.elements +=
                    object : Button<EditorScreen>(palette, buttonBarStage, buttonBarStage) {
                        override fun onLeftClick(xPercent: Float, yPercent: Float) {
                            super.onLeftClick(xPercent, yPercent)
                            main.screen = ScreenRegistry.getNonNull("newRemix")
                        }
                    }.apply {
                        this.location.set(screenWidth = size)
                        this.addLabel(ImageLabel(palette, this, this.stage).apply {
                            this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_new_button"))
                        })
                    }
            buttonBarStage.elements +=
                    object : Button<EditorScreen>(palette, buttonBarStage, buttonBarStage) {
                        override fun onLeftClick(xPercent: Float, yPercent: Float) {
                            super.onLeftClick(xPercent, yPercent)
                            main.screen = ScreenRegistry.getNonNull("openRemix")
                        }
                    }.apply {
                        this.location.set(screenWidth = size,
                                          screenX = size + padding)
                        this.addLabel(ImageLabel(palette, this, this.stage).apply {
                            this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_load_button"))
                        })
                    }
            buttonBarStage.elements +=
                    object : Button<EditorScreen>(palette, buttonBarStage, buttonBarStage) {
                        override fun onLeftClick(xPercent: Float, yPercent: Float) {
                            super.onLeftClick(xPercent, yPercent)
                            main.screen = ScreenRegistry.getNonNull("saveRemix")
                        }
                    }.apply {
                        this.location.set(screenWidth = size,
                                          screenX = size * 2 + padding * 2)
                        this.addLabel(ImageLabel(palette, this, this.stage).apply {
                            this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_save_button"))
                        })
                    }
            buttonBarStage.elements += UndoRedoButton(editor, true, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = size * 3 + padding * 3)
            }
            buttonBarStage.elements += UndoRedoButton(editor, false, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = size * 4 + padding * 4)
            }
            buttonBarStage.elements += MusicButton(editor, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = size * 5 + padding * 5)
            }

            buttonBarStage.elements += MetronomeButton(editor, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = size * 6 + padding * 6)
            }
            buttonBarStage.elements += TapalongToggleButton(editor, this@EditorStage, palette, buttonBarStage,
                                                            buttonBarStage).apply {
                this.location.set(screenX = size * 7 + padding * 7)
                this.location.set(screenWidth = pauseButton.location.screenX - this.location.screenX - padding)
            }

            // right aligned
            // info button
            buttonBarStage.elements += InfoButton(editor, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = 1f - size)
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_info_button"))
                })
            }
            // language button
            langButton = LangButton(editor, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = 1f - (size * 2 + padding))
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_language"))
                })
            }
            buttonBarStage.elements += langButton
            buttonBarStage.elements += FullscreenButton(editor, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = 1f - (size * 3 + padding * 2))
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_fullscreen"))
                })
            }
            buttonBarStage.elements += ResetWindowButton(editor, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = 1f - (size * 4 + padding * 3))
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_resetwindow"))
                })
            }
            buttonBarStage.elements += ThemeButton(editor, this, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = 1f - (size * 5 + padding * 4))
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_palette"))
                })
                this.enabled = true
            }
            buttonBarStage.elements += PresentationModeButton(editor, this@EditorStage, palette, buttonBarStage,
                                                              buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = 1f - (size * 6 + padding * 5))
            }
            buttonBarStage.elements += ViewButton(editor, this, palette, buttonBarStage,
                                                  buttonBarStage).apply {
                this.location.set(screenWidth = size,
                                  screenX = 1f - (size * 7 + padding * 6))
                this.addLabel(ImageLabel(palette, this, this.stage).apply {
                    this.image = TextureRegion(AssetRegistry.get<Texture>("ui_icon_views"))
                })
            }
            buttonBarStage.elements += SnapButton(editor, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size * 3,
                                  screenX = 1f - (size * 10 + padding * 7))
            }
            jumpToField = JumpToField(editor, palette, buttonBarStage, buttonBarStage).apply {
                this.location.set(screenWidth = size * 4,
                                  screenX = 1f - (size * 14 + padding * 8))
                this.textAlign = Align.center
                this.background = true
            }
            buttonBarStage.elements += jumpToField
        }

        this.updatePositions()
        this.updateSelected()
    }

    abstract inner class SelectableButton(palette: UIPalette, parent: UIElement<EditorScreen>,
                                          stage: Stage<EditorScreen>,
                                          val onLeftClickFunc: SelectableButton.(Float, Float) -> Unit = { x, y -> })
        : Button<EditorScreen>(palette, parent, stage) {

        val label: ImageLabel<EditorScreen> = ImageLabel(palette, this, stage).apply {
            this.renderType = ImageLabel.ImageRendering.RENDER_FULL
            this.image = TextureRegion(AssetRegistry.missingTexture)
        }

        var selected: Boolean = false
            set(value) {
                val old = field
                field = value
                if (field != old) {
                    this.removeLabel(selectedLabel)
                    if (field) {
                        this.addLabel(selectedLabel)
                        selectedLabel.onResize(this.location.realWidth, this.location.realHeight)
                    }
                }
            }
        abstract val selectedLabel: ImageLabel<EditorScreen>

        init {
            addLabel(label)
        }

        override fun onLeftClick(xPercent: Float, yPercent: Float) {
            super.onLeftClick(xPercent, yPercent)
            this.onLeftClickFunc(xPercent, yPercent)
        }
    }

    open inner class GameButton(val x: Int, val y: Int,
                                palette: UIPalette, parent: UIElement<EditorScreen>, stage: Stage<EditorScreen>,
                                f: SelectableButton.(Float, Float) -> Unit)
        : SelectableButton(palette, parent, stage, f), HasHoverText {

        var game: Game? = null
            set(value) {
                field = value
                this.visible = field != null
                if (value != null) {
                    this.label.image!!.setRegion(value.icon)
                } else {
                    this.selected = false
                }
            }

        override fun getHoverText(): String {
            return game?.name ?: ""
        }

        override val selectedLabel: ImageLabel<EditorScreen> = ImageLabel(palette, this, stage).apply {
            this.image = selectorRegion
        }

    }

    open inner class SeriesButton(val series: Series,
                                  palette: UIPalette, parent: UIElement<EditorScreen>, stage: Stage<EditorScreen>,
                                  onLeftClick: SelectableButton.(Float, Float) -> Unit)
        : SelectableButton(palette, parent, stage, onLeftClick), HasHoverText {

        override fun getHoverText(): String {
            return Localization[series.localization]
        }

        override val selectedLabel: ImageLabel<EditorScreen> = ImageLabel(palette, this, stage).apply {
            this.image = selectorRegionSeries
        }
    }

    open inner class PlaybackButton(val type: PlayState, palette: UIPalette, parent: UIElement<EditorScreen>,
                                    stage: Stage<EditorScreen>)
        : Button<EditorScreen>(palette, parent, stage) {

        private fun updateEnabledness() {
            this.enabled = false
            when (editor.remix.playState) {
                PlayState.STOPPED -> if (type == PlayState.PLAYING) enabled = true
                PlayState.PAUSED -> if (type != PlayState.PAUSED) enabled = true
                PlayState.PLAYING -> if (type != PlayState.PLAYING) enabled = true
            }
        }

        override fun onLeftClick(xPercent: Float, yPercent: Float) {
            super.onLeftClick(xPercent, yPercent)
            editor.remix.playState = type
        }

        override fun render(screen: EditorScreen, batch: SpriteBatch, shapeRenderer: ShapeRenderer) {
            updateEnabledness()
            super.render(screen, batch, shapeRenderer)
        }
    }

    open inner class ToolButton(val tool: Tool,
                                palette: UIPalette, parent: UIElement<EditorScreen>, stage: Stage<EditorScreen>,
                                onLeftClickFunc: SelectableButton.(Float, Float) -> Unit)
        : SelectableButton(palette, parent, stage, onLeftClickFunc), HasHoverText {
        override val selectedLabel: ImageLabel<EditorScreen> = ImageLabel(palette, this, stage).apply {
            this.image = selectorRegionSeries
        }

        override fun getHoverText(): String {
            return Localization[tool.nameId]
        }

        init {
            label.image = TextureRegion(tool.texture)
        }

        override fun onLeftClick(xPercent: Float, yPercent: Float) {
            super.onLeftClick(xPercent, yPercent)
            editor.updateMessageLabel()
        }
    }
}
