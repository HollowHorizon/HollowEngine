package ru.hollowhorizon.hollowstory.client.gui.widget.dialogue;

import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.util.text.*;
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget;
import ru.hollowhorizon.hollowstory.client.gui.widget.FadeInLabelWidget;

import java.util.List;

public class DialogueTextBox extends HollowWidget {
    private FadeInLabelWidget labelTop;
    private FadeInLabelWidget labelMiddle;
    private FadeInLabelWidget labelBottom;

    public DialogueTextBox(int x, int y, int width, int height) {
        super(x, y, width, height, NarratorChatListener.NO_TITLE);
        init();
    }

    @Override
    public void init() {
        this.getWidgets().clear();
        super.init();
        labelTop = new FadeInLabelWidget(this.x, this.y, this.width, this.height / 3);
        labelMiddle = new FadeInLabelWidget(this.x, this.y + this.height / 3, this.width, this.height / 3);
        labelBottom = new FadeInLabelWidget(this.x, this.y + this.height / 3 * 2, this.width, this.height / 3);
        this.addWidgets(labelTop, labelMiddle, labelBottom);

        labelTop.onFadeInComplete(() -> labelMiddle.resetTicker());
        labelMiddle.onFadeInComplete(() -> labelBottom.resetTicker());
    }

    public boolean isComplete() {
        return labelTop.isComplete() && labelMiddle.isComplete() && labelBottom.isComplete();
    }

    public void complete() {
        this.labelTop.setTicker(40);
        this.labelMiddle.setTicker(40);
        this.labelBottom.setTicker(40);
    }

    public String getText() {
        return labelTop.getText() + "\n" + labelMiddle.getText() + "\n" + labelBottom.getText();
    }

    public void setText(String textS) {
        this.reset();
        ITextComponent text = new StringTextComponent(textS);
        CharacterManager charactermanager = this.getFont().getSplitter();

        List<ITextProperties> lines = charactermanager.splitLines(text, (int) (this.width * 0.95F), Style.EMPTY);

        for (int i = 0; i < lines.size(); i++) {
            switch (i) {
                case 0:
                    labelTop.setText(lines.get(i).getString());
                    labelMiddle.setTicker(40);
                    labelBottom.setTicker(40);
                    break;
                case 1:
                    labelMiddle.setText(lines.get(i).getString());
                    labelBottom.setTicker(40);
                    break;
                case 2:
                    labelBottom.setText(lines.get(i).getString());
                    break;
                default:
                    break;
            }
        }
        labelTop.resetTicker();
    }

    public void reset() {
        labelTop.setText("");
        labelMiddle.setText("");
        labelBottom.setText("");
    }
}
