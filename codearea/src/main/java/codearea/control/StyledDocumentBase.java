package codearea.control;

import static codearea.control.ReadOnlyStyledDocument.ParagraphsPolicy.*;
import static codearea.control.TwoDimensional.Bias.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javafx.scene.control.IndexRange;

abstract class StyledDocumentBase<S, L extends List<Paragraph<S>>>
implements StyledDocument<S> {

    protected final L paragraphs;
    protected final TwoLevelNavigator navigator;

    protected StyledDocumentBase(L paragraphs) {
        this.paragraphs = paragraphs;
        navigator = new TwoLevelNavigator(
                () -> paragraphs.size(),
                i -> {
                    int len = paragraphs.get(i).length();
                    // add 1 for newline to every paragraph except last
                    return i == paragraphs.size()-1 ? len : len + 1;
                });
    }


    /***************************************************************************
     *                                                                         *
     * Queries                                                                 *
     *                                                                         *
     ***************************************************************************/

    @Override
    public Position offsetToPosition(int offset, Bias bias) {
        return navigator.offsetToPosition(offset, bias);
    }

    @Override
    public Position position(int row, int col) {
        return navigator.position(row, col);
    }

    @Override
    public String getText(IndexRange range) {
        return getText(range.getStart(), range.getEnd());
    }

    @Override
    public String getText(int start, int end) {
        return sub(
                start, end,
                p -> p.toString(),
                (p, a, b) -> p.substring(a, b),
                (List<String> ss) -> join(ss, "\n"));
    }

    @Override
    public String toString() {
        return getText();
    }

    @Override
    public char charAt(int index) {
        Position pos = offsetToPosition(index, Forward);
        return paragraphs.get(pos.getMajor()).charAt(pos.getMinor());
    }

    @Override
    public StyledDocument<S> subSequence(IndexRange range) {
        return subSequence(range.getStart(), range.getEnd());
    }

    @Override
    public StyledDocument<S> subSequence(int start, int end) {
        return sub(
                start, end,
                p -> p,
                (p, a, b) -> p.subSequence(a, b),
                (List<Paragraph<S>> pars) -> new ReadOnlyStyledDocument<S>(pars, ADOPT));
    }

    @Override
    public final StyledDocument<S> concat(StyledDocument<S> that) {
        List<Paragraph<S>> pars1 = this.getParagraphs();
        List<Paragraph<S>> pars2 = that.getParagraphs();
        int n1 = pars1.size();
        int n2 = pars2.size();
        List<Paragraph<S>> pars = new ArrayList<>(n1 + n2 - 1);
        pars.addAll(pars1.subList(0, n1 - 1));
        pars.add(pars1.get(n1 - 1).append(pars2.get(0)));
        pars.addAll(pars2.subList(1, n2));
        return new ReadOnlyStyledDocument<S>(pars, ADOPT);
    }

    @Override
    public S getStyleAt(int pos) {
        Position pos2D = navigator.offsetToPosition(pos, Forward);
        int line = pos2D.getMajor();
        int col = pos2D.getMinor();
        return paragraphs.get(line).getStyleAt(col);
    }

    @Override
    public S getStyleAt(int paragraph, int column) {
        return paragraphs.get(paragraph).getStyleAt(column);
    }

    @Override
    public StyleSpans<S> getStyleSpans(int from, int to) {
        Position start = offsetToPosition(from, Forward);
        Position end = start.offsetBy(to - from, Backward);
        int startParIdx = start.getMajor();
        int endParIdx = end.getMajor();

        int affectedPars = endParIdx - startParIdx + 1;
        List<StyleSpans<S>> subSpans = new ArrayList<>(affectedPars);

        if(startParIdx == endParIdx) {
            Paragraph<S> par = paragraphs.get(startParIdx);
            subSpans.add(par.getStyleRanges(start.getMinor(), end.getMinor()));
        } else {
            Paragraph<S> startPar = paragraphs.get(startParIdx);
            subSpans.add(startPar.getStyleRanges(start.getMinor(), startPar.length() + 1)); // +1 for the newline

            for(int i = startParIdx + 1; i < endParIdx; ++i) {
                Paragraph<S> par = paragraphs.get(i);
                subSpans.add(par.getStyleRanges(0, par.length() + 1)); // +1 for the newline
            }

            Paragraph<S> endPar = paragraphs.get(endParIdx);
            subSpans.add(endPar.getStyleRanges(0, end.getMinor()));
        }

        int n = subSpans.stream().mapToInt(sr -> sr.getSpanCount()).sum();
        StyleSpansBuilder<S> builder = new StyleSpansBuilder<>(n);
        for(StyleSpans<S> spans: subSpans) {
            for(StyleSpan<S> span: spans) {
                builder.add(span);
            }
        }

        return builder.create();
    }

    @Override
    public StyleSpans<S> getStyleSpans(int paragraph, int from, int to) {
        return paragraphs.get(paragraph).getStyleRanges(from, to);
    }


    /**************************************************************************
     *                                                                        *
     * Private methods                                                        *
     *                                                                        *
     **************************************************************************/

    private interface SubMap<A, B> {
        B subrange(A par, int start, int end);
    }

    /**
     * Returns a subrange of this document.
     * @param start
     * @param end
     * @param map maps a paragraph to an object of type {@code P}.
     * @param subMap maps a subrange of paragraph to an object of type {@code P}.
     * @param combine combines mapped paragraphs to form the result.
     * It is safe for the client code to take ownership of the list instance
     * passed to combine.
     * @param <P> type to which paragraphs are mapped.
     * @param <R> type of the resulting sub-document.
     */
    private <P, R> R sub(
            int start, int end,
            Function<Paragraph<S>, P> map,
            SubMap<Paragraph<S>, P> subMap,
            Function<List<P>, R> combine) {

        int length = end - start;

        Position start2D = navigator.offsetToPosition(start, Forward);
        Position end2D = start2D.offsetBy(length, Forward); // Forward to make sure newline is not lost
        int p1 = start2D.getMajor();
        int col1 = start2D.getMinor();
        int p2 = end2D.getMajor();
        int col2 = end2D.getMinor();

        List<P> pars = new ArrayList<>(p2 - p1 + 1);

        if(p1 == p2) {
            pars.add(subMap.subrange(paragraphs.get(p1), col1, col2));
        } else {
            Paragraph<S> par1 = paragraphs.get(p1);
            pars.add(subMap.subrange(par1, col1, par1.length()));

            for(int i = p1 + 1; i < p2; ++i) {
                pars.add(map.apply(paragraphs.get(i)));
            }

            pars.add(subMap.subrange(paragraphs.get(p2), 0, col2));
        }

        return combine.apply(pars);
    }

    /**
     * Joins a list of strings, using the given separator string.
     */
    private static String join(List<String> list, String sep) {
        int len = list.stream().mapToInt(s -> s.length()).sum() + (list.size()-1) * sep.length();
        StringBuilder sb = new StringBuilder(len);
        sb.append(list.get(0));
        for(int i = 1; i < list.size(); ++i) {
            sb.append(sep).append(list.get(i));
        }
        return sb.toString();
    }
}
