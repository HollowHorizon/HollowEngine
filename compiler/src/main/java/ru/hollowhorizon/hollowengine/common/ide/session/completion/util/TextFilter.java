package ru.hollowhorizon.hollowengine.common.ide.session.completion.util;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNls;

public class TextFilter extends PlainTextFilter {

    public TextFilter(@NonNls String value) {
        super(value);
    }

    public TextFilter(@NonNls String... values) {
        super(values);
    }

    public TextFilter(@NonNls String value1, @NonNls String value2) {
        super(value1, value2);
    }

    @Override
    protected String getTextByElement(final Object element) {
        if (element instanceof PsiType) {
            return ((PsiType) element).getPresentableText();
        } else {
            return super.getTextByElement(element);
        }
    }
}
