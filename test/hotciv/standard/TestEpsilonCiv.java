package hotciv.standard;

import hotciv.framework.*;

import org.junit.*;
import static org.junit.Assert.*;

public class TestEpsilonCiv {
    private Game game;

    @Before
        public void setUp() {
	ThreeKillWinStrategy killWinStrategy = 
	    new ThreeKillWinStrategy(new SimpleFightingStrategy());
	game = new GameImpl(new LinearAgingStrategy(),
			    killWinStrategy,
			    new VoidActionStrategy(),
			    new SimpleCityLayoutStrategy(),
			    new SimpleWorldLayoutStrategy(),
			    killWinStrategy);
    }

    private void endRound() {
	game.endOfTurn();
	game.endOfTurn();
    }

    @Test
	public void blueWinsTheGameAfterWinningThreeAttacks() {
	Position settlerPos = new Position(4, 3);
	Position legionPos = new Position(3, 2);
	Position archerPos = new Position(2, 0);
	Position intermediatePos = new Position(3, 1);
	Position cityPos = new Position(1, 1);

	// Go to Blue's turn
	game.endOfTurn();

	// Kill the Settler
	game.moveUnit(legionPos, settlerPos);
	
	// Wait one round
	endRound();

	// Move back
	game.moveUnit(settlerPos, legionPos);
	
	// Wait one round
	endRound();

	// Move one step left
	game.moveUnit(legionPos, intermediatePos);
	
	// Wait one round
	endRound();

	// Kill archer
	game.moveUnit(intermediatePos, archerPos);
	
	// Wait one round
	endRound();

	// At this point there should be no winner
	assertNull("No one should have won yet",
		   game.getWinner());

	// Kill new archer
	game.moveUnit(archerPos, cityPos);

	// Now Blue should have won
	assertEquals("Blue should have won by now",
		     Player.BLUE, game.getWinner());
    }
}
