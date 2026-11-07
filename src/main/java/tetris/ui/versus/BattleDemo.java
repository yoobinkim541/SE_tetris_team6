package tetris.ui.versus;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import javax.swing.*;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.MatchPhase;

/** Standalone S7 fixture; intentionally separate from the production single-player menu. */
public final class BattleDemo {
    private BattleDemo() { }
    public static void main(String[] args) {
        boolean colorBlind=Arrays.asList(args).contains("--color-blind");
        SwingUtilities.invokeLater(() -> open(colorBlind));
    }
    public static JFrame open(boolean colorBlind) {
        var frame=new JFrame("Sprint 7 B - Fake battle preview");
        var panel=new BattlePanel(colorBlind);
        var commands=new JLabel("Fake preview: key commands are recorded, gameplay is not simulated.");
        var fake=new FakeBattleClient();
        var subscription=fake.subscribe(panel::setSnapshot);
        boolean[] paused={false};
        var router=new BattleInputRouter(frame.getRootPane(),new BattleKeyMap(),command -> {
            fake.send(command);
            commands.setText(command.playerId()+" / "+command.action()+" / "+(command.pressed()?"press":"release")+" / #"+command.sequence());
            if(command.pressed() && command.action()==GameAction.PAUSE) {
                paused[0]=!paused[0];
                var snapshot=FakeBattleClient.sample();
                fake.publish(new MatchSnapshot(snapshot.matchId(),command.sequence(),snapshot.one(),snapshot.two(),
                    snapshot.remainingMillis(),paused[0]?MatchPhase.PAUSED:MatchPhase.PLAYING,null));
            }
            if(command.pressed() && command.action()==GameAction.QUIT) frame.dispose();
        },action -> !paused[0] || BattleKeyMap.isShared(action));
        router.resetForMatch(FakeBattleClient.sample().matchId());
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                router.close(); subscription.close(); fake.close();
            }
        });
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(panel,BorderLayout.CENTER); frame.add(commands,BorderLayout.SOUTH);
        frame.pack(); frame.setMinimumSize(new Dimension(480,400));
        frame.setLocationRelativeTo(null); frame.setVisible(true);
        return frame;
    }
}
