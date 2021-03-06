package codearea.control;

import static codearea.control.TwoDimensional.Bias.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.scene.control.Control;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Skin;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import reactfx.EventStream;
import reactfx.Hold;
import reactfx.Indicator;
import undofx.UndoManager;
import undofx.UndoManagerFactory;
import codearea.control.CssProperties.EditableProperty;
import codearea.control.CssProperties.FontProperty;
import codearea.skin.StyledTextAreaSkin;

import com.sun.javafx.Utils;

/**
 * Text editing control. Accepts user input (keyboard, mouse) and
 * provides API to assign style to text ranges. It is suitable for
 * syntax highlighting and rich-text editors.
 *
 * <p>Subclassing is allowed to define the type of style, e.g. inline
 * style or style classes.</p>
 *
 * @param <S> type of style that can be applied to text.
 */
public class StyledTextArea<S> extends Control
implements
        TextEditingArea<S>,
        EditActions<S>,
        ClipboardActions<S>,
        NavigationActions<S>,
        UndoActions<S>,
        TwoDimensional {

    private static final IndexRange EMPTY_RANGE = new IndexRange(0, 0);


    /**************************************************************************
     *                                                                        *
     * Properties                                                             *
     *                                                                        *
     * Properties affect behavior and/or appearance of this control.          *
     *                                                                        *
     * They are readable and writable by the client code and never change by  *
     * other means, i.e. they contain either the default value or the value   *
     * set by the client code.                                                *
     *                                                                        *
     **************************************************************************/

    // editable property
    private final BooleanProperty editable = new EditableProperty<>(this);
    @Override public final boolean isEditable() { return editable.get(); }
    @Override public final void setEditable(boolean value) { editable.set(value); }
    @Override public final BooleanProperty editableProperty() { return editable; }

    // wrapText property
    private final BooleanProperty wrapText = new SimpleBooleanProperty(this, "wrapText");
    @Override public final boolean isWrapText() { return wrapText.get(); }
    @Override public final void setWrapText(boolean value) { wrapText.set(value); }
    @Override public final BooleanProperty wrapTextProperty() { return wrapText; }

    // undo manager
    private UndoManager undoManager;
    @Override
    public UndoManager getUndoManager() { return undoManager; }
    @Override
    public void setUndoManager(UndoManagerFactory undoManagerFactory) {
        undoManager.close();
        undoManager = preserveStyle
                ? createRichUndoManager(undoManagerFactory)
                : createPlainUndoManager(undoManagerFactory);
    }

    // font property
    /**
     * The default font to use where font is not specified otherwise.
     */
    private final StyleableObjectProperty<Font> font = new FontProperty<>(this);
    public final StyleableObjectProperty<Font> fontProperty() { return font; }
    public final void setFont(Font value) { font.setValue(value); }
    public final Font getFont() { return font.getValue(); }


    /**************************************************************************
     *                                                                        *
     * Observables                                                            *
     *                                                                        *
     * Observables are "dynamic" (i.e. changing) characteristics of this      *
     * control. They are not directly settable by the client code, but change *
     * in response to user input and/or API actions.                          *
     *                                                                        *
     **************************************************************************/

    // text
    @Override public final String getText() { return content.getText(); }
    @Override public final ObservableStringValue textProperty() { return content.textProperty(); }

    // length
    @Override public final int getLength() { return content.getLength(); }
    @Override public final ObservableIntegerValue lengthProperty() { return content.lengthProperty(); }

    // caret position
    private final ReadOnlyIntegerWrapper caretPosition = new ReadOnlyIntegerWrapper(this, "caretPosition", 0);
    @Override public final int getCaretPosition() { return caretPosition.get(); }
    @Override public final ReadOnlyIntegerProperty caretPositionProperty() { return caretPosition.getReadOnlyProperty(); }

    // selection anchor
    private final ReadOnlyIntegerWrapper anchor = new ReadOnlyIntegerWrapper(this, "anchor", 0);
    @Override public final int getAnchor() { return anchor.get(); }
    @Override public final ReadOnlyIntegerProperty anchorProperty() { return anchor.getReadOnlyProperty(); }

    // selection
    private final ReadOnlyObjectWrapper<IndexRange> selection = new ReadOnlyObjectWrapper<IndexRange>(this, "selection");
    @Override public final IndexRange getSelection() { return selection.getValue(); }
    @Override public final ReadOnlyObjectProperty<IndexRange> selectionProperty() { return selection.getReadOnlyProperty(); }

    // selected text
    private final ObservableStringValue selectedText;
    @Override public final String getSelectedText() { return selectedText.get(); }
    @Override public final ObservableStringValue selectedTextProperty() { return selectedText; }

    // current paragraph index
    private final ObservableIntegerValue currentParagraph;
    @Override public final int getCurrentParagraph() { return currentParagraph.get(); }
    @Override public final ObservableIntegerValue currentParagraph() { return currentParagraph; }

    // caret column
    private final ObservableIntegerValue caretColumn;
    @Override public final int getCaretColumn() { return caretColumn.get(); }

    // paragraphs
    private final ObservableList<Paragraph<S>> paragraphs;
    @Override public ObservableList<Paragraph<S>> getParagraphs() {
        return paragraphs;
    }

    // beingUpdated
    private final Indicator beingUpdated = new Indicator();
    public Indicator beingUpdatedProperty() { return beingUpdated; }
    public boolean isBeingUpdated() { return beingUpdated.isOn(); }


    /**************************************************************************
     *                                                                        *
     * Event streams                                                          *
     *                                                                        *
     **************************************************************************/

    // text changes
    @Override
    public final EventStream<PlainTextChange> plainTextChanges() { return content.plainTextChanges(); }

    // rich text changes
    @Override
    public final EventStream<RichTextChange<S>> richChanges() { return content.richChanges(); }


    /**************************************************************************
     *                                                                        *
     * Private fields                                                         *
     *                                                                        *
     **************************************************************************/

    private Position selectionStart2D;
    private Position selectionEnd2D;

    /**
     * content model
     */
    private final EditableStyledDocument<S> content;

    /**
     * Style used by default when no other style is provided.
     */
    private final S initialStyle;

    /**
     * Style applicator used by the default skin.
     */
    private final BiConsumer<Text, S> applyStyle;

    /**
     * Indicates whether style should be preserved on undo/redo,
     * copy/paste and text move.
     * TODO: Currently, only undo/redo respect this flag.
     */
    private final boolean preserveStyle;


    /**************************************************************************
     *                                                                        *
     * Constructors                                                           *
     *                                                                        *
     **************************************************************************/

    /**
     * Creates a text area with empty text content.
     *
     * @param initialStyle style to use in places where no other style is
     * specified (yet).
     * @param applyStyle function that, given a {@link Text} node and
     * a style, applies the style to the text node. This function is
     * used by the default skin to apply style to text nodes.
     */
    public StyledTextArea(S initialStyle, BiConsumer<Text, S> applyStyle) {
        this(initialStyle, applyStyle, true);
    }

    public <C> StyledTextArea(S initialStyle, BiConsumer<Text, S> applyStyle,
            boolean preserveStyle) {
        this.initialStyle = initialStyle;
        this.applyStyle = applyStyle;
        this.preserveStyle = preserveStyle;
        content = new EditableStyledDocument<>(initialStyle);
        paragraphs = content.getParagraphs();

        undoManager = preserveStyle
                ? createRichUndoManager(UndoManagerFactory.unlimitedHistoryFactory())
                : createPlainUndoManager(UndoManagerFactory.unlimitedHistoryFactory());

        ObservableValue<Position> caretPosition2D = BindingFactories.createBinding(caretPosition, p -> content.offsetToPosition(p, Forward));
        currentParagraph = BindingFactories.createIntegerBinding(caretPosition2D, p -> p.getMajor());
        caretColumn = BindingFactories.createIntegerBinding(caretPosition2D, p -> p.getMinor());

        selection.addListener(obs -> {
            IndexRange sel = selection.get();
            selectionStart2D = offsetToPosition(sel.getStart(), Forward);
            selectionEnd2D = selectionStart2D.offsetBy(sel.getLength(), Backward);
        });
        selection.set(EMPTY_RANGE);

        selectedText = new StringBinding() {
            { bind(selection, textProperty()); }
            @Override protected String computeValue() {
                return content.getText(selection.get());
            }
        };

        getStyleClass().add("styled-text-area");
    }


    /**************************************************************************
     *                                                                        *
     * Queries                                                                *
     *                                                                        *
     * Queries are parameterized observables.                                 *
     *                                                                        *
     **************************************************************************/

    @Override
    public final String getText(int start, int end) {
        return content.getText(start, end);
    }

    @Override
    public String getText(int paragraph) {
        return paragraphs.get(paragraph).toString();
    }

    /**
     * Returns the selection range in the given paragraph.
     */
    public IndexRange getParagraphSelection(int paragraph) {
        int startPar = selectionStart2D.getMajor();
        int endPar = selectionEnd2D.getMajor();

        if(paragraph < startPar || paragraph > endPar) {
            return EMPTY_RANGE;
        }

        int start = paragraph == startPar ? selectionStart2D.getMinor() : 0;
        int end = paragraph == endPar ? selectionEnd2D.getMinor() : paragraphs.get(paragraph).length();

        return new IndexRange(start, end);
    }

    /**
     * Returns the style of the character at the given position.
     */
    public S getStyleAt(int pos) {
        return content.getStyleAt(pos);
    }

    /**
     * Returns the styles in the given character range.
     */
    public StyleSpans<S> getStyleSpans(int from, int to) {
        return content.getStyleSpans(from, to);
    }

    /**
     * Returns the styles in the given character range.
     */
    public StyleSpans<S> getStyleSpans(IndexRange range) {
        return getStyleSpans(range.getStart(), range.getEnd());
    }

    /**
     * Returns the style of the character at the given position in the given
     * paragraph. If {@code pos} is beyond the end of the paragraph, the style
     * at the end of line is returned. If {@code pos} is negative, it is the
     * same as if it was 0.
     */
    public S getStyleAt(int paragraph, int pos) {
        return content.getStyleAt(paragraph, pos);
    }

    /**
     * Returns the styles in the given character range of the given paragraph.
     */
    public StyleSpans<S> getStyleSpans(int paragraph, int from, int to) {
        return content.getStyleSpans(paragraph, from, to);
    }

    /**
     * Returns the styles in the given character range of the given paragraph.
     */
    public StyleSpans<S> getStyleSpans(int paragraph, IndexRange range) {
        return getStyleSpans(paragraph, range.getStart(), range.getEnd());
    }

    @Override
    public Position position(int row, int col) {
        return content.position(row, col);
    }

    @Override
    public Position offsetToPosition(int charOffset, Bias bias) {
        return content.offsetToPosition(charOffset, bias);
    }


    /**************************************************************************
     *                                                                        *
     * Actions                                                                *
     *                                                                        *
     * Actions change the state of this control. They typically cause a       *
     * change of one or more observables and/or produce an event.             *
     *                                                                        *
     **************************************************************************/

    /**
     * Sets style for the given character range.
     */
    public void setStyle(int from, int to, S style) {
        try(Hold h = beingUpdated.on()) {
            content.setStyle(from, to, style);
        }
    }

    /**
     * Sets style for the whole paragraph.
     */
    public void setStyle(int paragraph, S style) {
        try(Hold h = beingUpdated.on()) {
            content.setStyle(paragraph, style);
        }
    }

    /**
     * Sets style for the given range relative in the given paragraph.
     */
    public void setStyle(int paragraph, int from, int to, S style) {
        try(Hold h = beingUpdated.on()) {
            content.setStyle(paragraph, from, to, style);
        }
    }

    /**
     * Set multiple style ranges at once. This is equivalent to
     * <pre>
     * for(StyleSpan{@code <S>} span: styleSpans) {
     *     setStyle(from, from + span.getLength(), span.getStyle());
     *     from += span.getLength();
     * }
     * </pre>
     * but the actual implementation is more efficient.
     */
    public void setStyleSpans(int from, StyleSpans<? extends S> styleSpans) {
        try(Hold h = beingUpdated.on()) {
            content.setStyleSpans(from, styleSpans);
        }
    }

    /**
     * Set multiple style ranges of a paragraph at once. This is equivalent to
     * <pre>
     * for(StyleSpan{@code <S>} span: styleSpans) {
     *     setStyle(paragraph, from, from + span.getLength(), span.getStyle());
     *     from += span.getLength();
     * }
     * </pre>
     * but the actual implementation is more efficient.
     */
    public void setStyleSpans(int paragraph, int from, StyleSpans<? extends S> styleSpans) {
        try(Hold h = beingUpdated.on()) {
            content.setStyleSpans(paragraph, from, styleSpans);
        }
    }

    /**
     * Resets the style of the given range to the initial style.
     */
    public void clearStyle(int from, int to) {
        setStyle(from, to, initialStyle);
    }

    /**
     * Resets the style of the given paragraph to the initial style.
     */
    public void clearStyle(int paragraph) {
        setStyle(paragraph, initialStyle);
    }

    /**
     * Resets the style of the given range in the given paragraph
     * to the initial style.
     */
    public void clearStyle(int paragraph, int from, int to) {
        setStyle(paragraph, from, to, initialStyle);
    }

    @Override
    public void replaceText(int start, int end, String text) {
        try(Hold h = beingUpdated.on()) {
            start = Utils.clamp(0, start, getLength());
            end = Utils.clamp(0, end, getLength());

            content.replaceText(start, end, text);

            int newCaretPos = start + text.length();
            selectRange(newCaretPos, newCaretPos);
        }
    }

    @Override
    public void replace(int start, int end, StyledDocument<S> replacement) {
        try(Hold h = beingUpdated.on()) {
            start = Utils.clamp(0, start, getLength());
            end = Utils.clamp(0, end, getLength());

            content.replace(start, end, replacement);

            int newCaretPos = start + replacement.length();
            selectRange(newCaretPos, newCaretPos);
        }
    }

    @Override
    public void selectRange(int anchor, int caretPosition) {
        try(Hold h = beingUpdated.on()) {
            this.caretPosition.set(Utils.clamp(0, caretPosition, getLength()));
            this.anchor.set(Utils.clamp(0, anchor, getLength()));
            this.selection.set(IndexRange.normalize(getAnchor(), getCaretPosition()));
        }
    }

    @Override
    public void positionCaret(int pos) {
        try(Hold h = beingUpdated.on()) {
            caretPosition.set(pos);
        }
    }


    /**************************************************************************
     *                                                                        *
     * Look & feel                                                            *
     *                                                                        *
     **************************************************************************/

    @Override
    protected Skin<?> createDefaultSkin() {
        return new StyledTextAreaSkin<S>(this, applyStyle);
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return Arrays.<CssMetaData<? extends Styleable, ?>>asList(
                font.getCssMetaData());
    }


    /**************************************************************************
     *                                                                        *
     * Private methods                                                        *
     *                                                                        *
     **************************************************************************/

    private UndoManager createPlainUndoManager(UndoManagerFactory factory) {
        Consumer<PlainTextChange> apply = change -> replaceText(change.getPosition(), change.getPosition() + change.getRemoved().length(), change.getInserted());
        Consumer<PlainTextChange> undo = change -> replaceText(change.getPosition(), change.getPosition() + change.getInserted().length(), change.getRemoved());
        BiFunction<PlainTextChange, PlainTextChange, Optional<PlainTextChange>> merge = (change1, change2) -> change1.mergeWith(change2);
        return factory.create(plainTextChanges(), apply, undo, merge);
    }

    private UndoManager createRichUndoManager(UndoManagerFactory factory) {
        Consumer<RichTextChange<S>> apply = change -> replace(change.getPosition(), change.getPosition() + change.getRemoved().length(), change.getInserted());
        Consumer<RichTextChange<S>> undo = change -> replace(change.getPosition(), change.getPosition() + change.getInserted().length(), change.getRemoved());
        BiFunction<RichTextChange<S>, RichTextChange<S>, Optional<RichTextChange<S>>> merge = (change1, change2) -> change1.mergeWith(change2);
        return factory.create(richChanges(), apply, undo, merge);
    }
}
