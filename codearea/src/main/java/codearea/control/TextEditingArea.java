/*
 * Copyright (c) 2013, Tomas Mikula. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package codearea.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.ObservableList;
import javafx.scene.control.IndexRange;
import reactfx.EventStream;

/**
 * Interface for a text editing control.
 *
 * Defines the core methods. Other interfaces define default
 * higher-level methods implemented on top of the core methods.
 *
 * @param <S> type of style that can be applied to text.
 */
public interface TextEditingArea<S> {

    /*******************
     *                 *
     *   Observables   *
     *                 *
     *******************/

    /**
     * The number of characters in this text-editing area.
     */
    int getLength();
    ObservableIntegerValue lengthProperty();

    /**
     * Text content of this text-editing area.
     */
    String getText();
    ObservableStringValue textProperty();

    /**
     * The current position of the caret, as a character offset in the text.
     *
     * Most of the time, caret is at the boundary of the selection (if there
     * is any selection). However, there are circumstances when the caret is
     * positioned inside or outside the selected text. For example, when the
     * user is dragging the selected text, the caret moves with the cursor
     * to point at the position where the selected text moves upon release.
     */
    int getCaretPosition();
    ReadOnlyIntegerProperty caretPositionProperty();

    /**
     * The anchor of the selection.
     * If there is no selection, this is the same as caret position.
     */
    int getAnchor();
    ReadOnlyIntegerProperty anchorProperty();

    /**
     * The selection range.
     *
     * One boundary is always equal to anchor, and the other one is most
     * of the time equal to caret position.
     */
    IndexRange getSelection();
    ReadOnlyObjectProperty<IndexRange> selectionProperty();

    /**
     * The selected text.
     */
    String getSelectedText();
    ObservableStringValue selectedTextProperty();

    /**
     * Index of the current paragraph, i.e. the paragraph with the caret.
     */
    int getCurrentParagraph();
    ObservableIntegerValue currentParagraph();

    /**
     * The caret position within the current paragraph.
     */
    int getCaretColumn();

    /**
     * Unmodifiable observable list of paragraphs in this text area.
     */
    ObservableList<Paragraph<S>> getParagraphs();


    /*********************
     *                   *
     *   Event streams   *
     *                   *
     *********************/

    /**
     * Stream of text changes.
     */
    EventStream<PlainTextChange> plainTextChanges();

    /**
     * Stream of rich text changes.
     */
    EventStream<RichTextChange<S>> richChanges();


    /***************
     *             *
     *   Queries   *
     *             *
     ***************/

    /**
     * Returns text content of the given paragraph.
     */
    String getText(int paragraphIndex);

    /**
     * Returns text content of the given character range.
     */
    String getText(int start, int end);


    /******************
     *                *
     *   Properties   *
     *                *
     ******************/

    /**
     * Indicates whether this text area can be edited by the user.
     * Note that this property doesn't affect editing through the API.
     */
    boolean isEditable();
    void setEditable(boolean value);
    BooleanProperty editableProperty();

    /**
     * When a run of text exceeds the width of the text region,
     * then this property indicates whether the text should wrap
     * onto another line.
     */
    boolean isWrapText();
    void setWrapText(boolean value);
    BooleanProperty wrapTextProperty();


    /***************
     *             *
     *   Actions   *
     *             *
     ***************/

    /**
     * Positions the anchor and caretPosition explicitly,
     * effectively creating a selection.
     */
    void selectRange(int anchor, int caretPosition);

    /**
     * Replaces a range of characters with the given text.
     *
     * It must hold {@code 0 <= start <= end <= getLength()}.
     *
     * @param start Start index of the range to replace, inclusive.
     * @param end End index of the range to replace, exclusive.
     * @param text The text to put in place of the deleted range.
     * It must not be null.
     */
    void replaceText(int start, int end, String text);

    /**
     * Replaces a range of characters with the given text.
     *
     * @param range The range to replace. It must not be null.
     * @param text The text to put in place of the deleted range.
     * It must not be null.
     *
     * @see #replaceText(int, int, String)
     */
    default void replaceText(IndexRange range, String text) {
        replaceText(range.getStart(), range.getEnd(), text);
    }

    void replace(int start, int end, StyledDocument<S> replacement);

    /**
     * Positions only the caret. Doesn't move the anchor and doesn't change
     * the selection. Can be used to achieve the special case of positioning
     * the caret outside or inside the selection, as opposed to always being
     * at the boundary. Use with care.
     */
    public void positionCaret(int pos);
}
