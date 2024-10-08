package mage.client.game;

import mage.client.MageFrame;
import mage.client.SessionHandler;
import mage.client.cards.BigCard;
import mage.client.chat.ChatPanelBasic;
import mage.client.dialog.MageDialog;
import mage.client.util.audio.AudioManager;
import mage.client.util.gui.ArrowBuilder;
import mage.constants.PlayerAction;
import mage.constants.TurnPhase;
import mage.util.ThreadUtils;
import mage.util.XmageThreadFactory;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static mage.constants.Constants.Option.*;

/**
 * Game GUI: feedback panel (over hand) with current priority and possible actions like done/cancel/special buttons
 * <p>
 * Warning, it's contains only clickable button, but all other logic done in helper panel
 *
 * @author BetaSteward_at_googlemail.com, JayDi85
 */
public class FeedbackPanel extends javax.swing.JPanel {

    private static final Logger LOGGER = Logger.getLogger(FeedbackPanel.class);

    public enum FeedbackMode {
        INFORM, QUESTION, CONFIRM, CANCEL, SELECT, END
    }

    private UUID gameId;
    private FeedbackMode mode;
    private MageDialog connectedDialog;
    private ChatPanelBasic connectedChatPanel;
    private Map<String, Serializable> lastOptions = new HashMap<>();

    private static final int AUTO_CLOSE_END_DIALOG_TIMEOUT_SECS = 8;
    private static final ScheduledExecutorService AUTO_CLOSE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(
            new XmageThreadFactory(ThreadUtils.THREAD_PREFIX_CLIENT_AUTO_CLOSE_TIMER)
    );

    public FeedbackPanel() {
        customInitComponents();
    }

    public void init(UUID gameId, BigCard bigCard) {
        this.gameId = gameId;
        helper.init(gameId, bigCard);
        setGUISize();
    }

    public void changeGUISize() {
        setGUISize();
        helper.changeGUISize();
    }

    private void setGUISize() {
    }

    public void prepareFeedback(FeedbackMode mode, String basicMessage, String additionalMessage, boolean special, Map<String, Serializable> options,
                                boolean gameNeedUserFeedback, TurnPhase gameTurnPhase) {
        synchronized (this) {
            this.lastOptions = options;
            this.mode = mode;
        }

        // build secondary message (will use smaller font)
        java.util.ArrayList<String> secondaryMessages = new ArrayList<>();
        if (additionalMessage != null && !additionalMessage.isEmpty()) {
            // client side additional info like active priority/player
            secondaryMessages.add(additionalMessage);
        }
        String serverSideAdditionalMessage = options != null && options.containsKey(SECOND_MESSAGE) ? (String) options.get(SECOND_MESSAGE) : null;
        if (serverSideAdditionalMessage != null && !serverSideAdditionalMessage.isEmpty()) {
            // server side additional info like card/source info
            secondaryMessages.add(serverSideAdditionalMessage);
        }

        this.helper.setMessages(basicMessage, String.join("<br>", secondaryMessages));
        this.helper.setOriginalId(null); // reference to the feedback causing ability

        switch (this.mode) {
            case INFORM:
                setButtonState("", "", mode);
                break;
            case QUESTION:
                setButtonState("Yes", "No", mode);
                if (options != null && options.containsKey(ORIGINAL_ID)) {
                    // allows yes/no auto-answers for ability related
                    this.helper.setOriginalId((UUID) options.get(ORIGINAL_ID));
                }
                if (options != null && options.containsKey(AUTO_ANSWER_MESSAGE)) {
                    // Uses a filtered message for remembering choice if the original message contains a self-reference
                    this.helper.setAutoAnswerMessage((String) options.get(AUTO_ANSWER_MESSAGE));
                } else {
                    this.helper.setAutoAnswerMessage(basicMessage);
                }
                break;
            case CONFIRM:
                setButtonState("OK", "Cancel", mode);
                break;
            case CANCEL:
                setButtonState("", "Cancel", mode);
                this.helper.setUndoEnabled(false);
                break;
            case SELECT:
                setButtonState("", "Done", mode);
                break;
            case END:
                setButtonState("", "Close game", mode);
                ArrowBuilder.getBuilder().removeAllArrows(gameId);
                endWithTimeout();
                break;
        }
        if (options != null && options.containsKey(SPECIAL_BUTTON)) {
            this.setSpecial((String) options.get(SPECIAL_BUTTON), true);
        } else {
            this.setSpecial("Special", special);
        }

        requestFocusIfPossible();
        updateOptions(options);

        this.helper.setLinks(btnLeft, btnRight, btnSpecial, btnUndo);

        this.helper.setVisible(true);
        this.helper.setGameNeedFeedback(gameNeedUserFeedback, gameTurnPhase);
        this.helper.autoSizeButtonsAndFeedbackState();

        this.revalidate();
    }

    private void setButtonState(String leftText, String rightText, FeedbackMode mode) {
        btnLeft.setVisible(!leftText.isEmpty());
        btnLeft.setText(leftText);
        btnRight.setVisible(!rightText.isEmpty());
        btnRight.setText(rightText);
        this.helper.setState(leftText, !leftText.isEmpty(), rightText, !rightText.isEmpty(), mode);
    }

    private void setSpecial(String text, boolean visible) {
        this.btnSpecial.setText(text);
        this.btnSpecial.setVisible(visible);
        this.helper.setSpecial(text, visible);
    }

    /**
     * Close game window by pressing OK button after 8 seconds
     */
    private void endWithTimeout() {
        // TODO: add auto-close disable, e.g. keep opened game and chat for longer period like 5 minutes
        Runnable task = () -> {
            SwingUtilities.invokeLater(() -> {
                LOGGER.info("Ending game...");
                Component c = MageFrame.getGame(gameId);
                while (c != null && !(c instanceof GamePane)) {
                    c = c.getParent();
                }
                if (c != null && c.isVisible()) { // check if GamePanel still visible
                    FeedbackPanel.this.btnRight.doClick();
                }
            });
        };
        AUTO_CLOSE_EXECUTOR.schedule(task, AUTO_CLOSE_END_DIALOG_TIMEOUT_SECS, TimeUnit.SECONDS);
    }

    public void updateOptions(Map<String, Serializable> options) {
        this.lastOptions = options;

        if (this.lastOptions != null) {
            if (this.lastOptions.containsKey("UI.left.btn.text")) {
                String text = (String) this.lastOptions.get("UI.left.btn.text");
                this.btnLeft.setText(text);
                this.helper.setLeft(text, !text.isEmpty());
            }
            if (this.lastOptions.containsKey("UI.right.btn.text")) {
                String text = (String) this.lastOptions.get("UI.right.btn.text");
                this.btnRight.setText(text);
                this.helper.setRight(text, !text.isEmpty());
            }
            updateConnectedDialog((MageDialog) this.lastOptions.getOrDefault("dialog", null));
            this.helper.autoSizeButtonsAndFeedbackState();
        } else {
            updateConnectedDialog(null);
        }
    }

    private void updateConnectedDialog(MageDialog newDialog) {
        if (this.connectedDialog != null && this.connectedDialog != newDialog) {
            // remove old
            this.connectedDialog.removeDialog();
        }
        this.connectedDialog = newDialog;
        if (this.connectedDialog != null) {
            this.connectedDialog.setVisible(true);
        }
    }

    // Issue 256: Chat+Feedback panel: request focus prevents players from chatting
    // Issue #1054: XMage steals window focus whenever the screen updates
    private void requestFocusIfPossible() {
        boolean requestFocusAllowed = true;
        if (MageFrame.getInstance().getFocusOwner() == null) {
            requestFocusAllowed = false;
        } else if (connectedChatPanel != null && connectedChatPanel.getTxtMessageInputComponent() != null) {
            if (connectedChatPanel.getTxtMessageInputComponent().hasFocus()) {
                requestFocusAllowed = false;
            }
        }
        if (requestFocusAllowed) {
            this.btnRight.requestFocus();
            this.helper.requestFocus();
        }
    }

    public void doClick() {
        this.btnRight.doClick();
    }

    public void clear() {
        this.btnLeft.setVisible(false);
        this.btnRight.setVisible(false);
        this.btnSpecial.setVisible(false);
    }

    private void customInitComponents() {
        btnRight = new javax.swing.JButton();
        btnLeft = new javax.swing.JButton();
        btnSpecial = new javax.swing.JButton();
        btnUndo = new javax.swing.JButton();
        btnUndo.setVisible(true);

        setBackground(new java.awt.Color(0, 0, 0, 80));

        btnRight.setText("Cancel");
        btnRight.addActionListener(evt -> btnRightActionPerformed(evt));

        btnLeft.setText("OK");
        btnLeft.addActionListener(evt -> btnLeftActionPerformed(evt));

        btnSpecial.setText("Special");
        btnSpecial.addActionListener(evt -> btnSpecialActionPerformed(evt));

        btnUndo.setText("Undo");
        btnUndo.addActionListener(evt -> btnUndoActionPerformed(evt));

    }

    private void btnRightActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRightActionPerformed
        updateConnectedDialog(null);
        if (mode == FeedbackMode.SELECT && (evt.getModifiers() & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
            SessionHandler.sendPlayerInteger(gameId, 0);
        } else if (mode == FeedbackMode.END) {
            GamePanel gamePanel = MageFrame.getGame(gameId);
            if (gamePanel != null) {
                gamePanel.removeGame();
            }
        } else {
            SessionHandler.sendPlayerBoolean(gameId, false);
        }
        //AudioManager.playButtonOk();
    }//GEN-LAST:event_btnRightActionPerformed

    private void btnLeftActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLeftActionPerformed
        SessionHandler.sendPlayerBoolean(gameId, true);
        AudioManager.playButtonCancel();
    }//GEN-LAST:event_btnLeftActionPerformed

    private void btnSpecialActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSpecialActionPerformed
        SessionHandler.sendPlayerString(gameId, "special");
    }//GEN-LAST:event_btnSpecialActionPerformed

    private void btnUndoActionPerformed(java.awt.event.ActionEvent evt) {
        SessionHandler.sendPlayerAction(PlayerAction.UNDO, gameId, null);
    }

    public void setHelperPanel(HelperPanel helper) {
        this.helper = helper;
    }

    public FeedbackMode getMode() {
        return this.mode;
    }

    public void setConnectedChatPanel(ChatPanelBasic chatPanel) {
        this.connectedChatPanel = chatPanel;
    }

    public void pressOKYesOrDone() {
        if (btnLeft.getText().equals("OK") || btnLeft.getText().equals("Yes")) {
            btnLeft.doClick();
        } else if (btnRight.getText().equals("OK") || btnRight.getText().equals("Yes") || btnRight.getText().equals("Done")) {
            btnRight.doClick();
        }
    }

    public void allowUndo(int bookmark) {
        this.helper.setUndoEnabled(true);
    }

    public void disableUndo() {
        this.helper.setUndoEnabled(false);
    }

    private javax.swing.JButton btnLeft;
    private javax.swing.JButton btnRight;
    private javax.swing.JButton btnSpecial;
    private javax.swing.JButton btnUndo;
    private HelperPanel helper;
}
