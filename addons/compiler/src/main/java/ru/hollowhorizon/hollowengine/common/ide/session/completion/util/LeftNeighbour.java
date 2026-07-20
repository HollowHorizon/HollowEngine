package ru.hollowhorizon.hollowengine.common.ide.session.completion.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.PositionElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

public class LeftNeighbour extends PositionElementFilter {

    public LeftNeighbour(ElementFilter filter){
        setFilter(filter);
    }

    @Override
    public boolean isAcceptable(Object element, PsiElement context){
        if (!(element instanceof PsiElement)) return false;
        final PsiElement previous = searchNonSpaceNonCommentBack((PsiElement) element);
        if(previous != null){
            return getFilter().isAcceptable(previous, context);
        }
        return false;
    }

    @Override
    public String toString(){
        return "left(" +getFilter()+")";
    }

    private static @Nullable PsiElement searchNonSpaceNonCommentBack(PsiElement element) {
        return element == null ? null : PsiTreeUtil.prevCodeLeaf(element);
    }
}
