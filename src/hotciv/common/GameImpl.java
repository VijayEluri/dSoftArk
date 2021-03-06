package hotciv.common;

import hotciv.framework.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Collection;

/** Skeleton implementation of HotCiv.
 
This source code is from the book 
"Flexible, Reliable Software:
Using Patterns and Agile Development"
published 2010 by CRC Press.
Author: 
Henrik B Christensen 
Computer Science Department
Aarhus University
   
This source code is provided WITHOUT ANY WARRANTY either 
expressed or implied. You may study, use, modify, and 
distribute it for non-commercial purposes. For any 
commercial use, see http://www.baerbak.com/
*/

public class GameImpl implements Game {
    private Player inTurn;
    private int round;
    private Map<Position, Unit> units;
    private Map<Position, City> cities;
    private Map<Position, Tile> world;
    private AgingStrategy agingStrategy;
    private WinningStrategy winningStrategy;
    private ActionStrategy actionStrategy;
    private FightingStrategy fightingStrategy;
    private GameEventController eventController;
    private Position tileFocus;

    public GameImpl(StrategyFactory strategyFactory) {
        this.eventController = strategyFactory.getEventController();

	this.agingStrategy = strategyFactory.getAgingStrategy();
	this.winningStrategy = strategyFactory.getWinningStrategy();
        this.actionStrategy = strategyFactory.getActionStrategy();
	this.fightingStrategy = strategyFactory.getFightingStrategy();
	inTurn = Player.RED;
	round = 0;

	units = new HashMap<Position, Unit>();
        units.put(new Position(2,0), UnitImpl.create(GameConstants.ARCHER,
                                Player.RED, round - 1));
        units.put(new Position(4,3), UnitImpl.create(GameConstants.SETTLER,
                                Player.RED, round - 1));
        units.put(new Position(3,2), UnitImpl.create(GameConstants.LEGION,
                                Player.BLUE, round - 1));

	cities = strategyFactory.getCityLayoutStrategy().getCities();
	world = strategyFactory.getWorldLayoutStrategy().getWorld();

        tileFocus = new Position(0,0);
    }

    public GameEventController getEventController() {
        return eventController;
    }

    public Tile getTileAt( Position p ) {
        return world.get(p);
    }

    public Unit getUnitAt( Position p ) {
	return units.get(p);
    }

    public City getCityAt( Position p ) {
	return cities.get(p);
    }

    public Player getPlayerInTurn() {
	return inTurn;
    }

    public Player getWinner() {
	return winningStrategy.getWinner(this);
    }
    
    public int getAge() {
	return agingStrategy.getYear(round);
    }

    private boolean unitCanMoveOnTile(Tile t) {
	String tileType = t.getTypeString();
	return !tileType.equals(GameConstants.MOUNTAINS)
	    && !tileType.equals(GameConstants.OCEANS);
    }

    private boolean isValidMove(Position from, Position to) {
	Tile targetTile = getTileAt(to);
	boolean legalTileType = unitCanMoveOnTile(targetTile);
	boolean legalDistance = Math.abs(to.getRow() - from.getRow()) <= 1
	    && Math.abs(to.getColumn() - from.getColumn()) <= 1;

	Unit u = getUnitAt(from);
        boolean isFortified = u.isFortified();
	Player p = u.getOwner();
	boolean isOwner = (p == inTurn);

	boolean hasMoved = u.getLastMoved() == round;

        return legalTileType && legalDistance && isOwner && !isFortified 
	    && !hasMoved;
    }

    private void executeUnitMove(Position from, Position to) {
	Unit u = units.get(from);
	units.remove(from);
	Unit newUnit = u.withLastMoved(round);
	units.put(to, newUnit);
	if (getCityAt(to) != null) {
	    City conquered = getCityAt(to);
	    cities.put(to, new CityImpl(u.getOwner(), 
					conquered.getProduction(),
					conquered.getProductionAmount()));
	}
    }

    public boolean moveUnit(Position from, Position to) {
	if (!isValidMove(from, to))
	    return false;

	if (units.get(to) != null) {
	    if (fightingStrategy.attackerWins(this, from, to)) {
		executeUnitMove(from, to);
                eventController.dispatch("ATTACKER_WON", units.get(to).getOwner());
                eventController.dispatch("WORLD_CHANGED", to);
	    } else {
                units.remove(from);
            }
	}
        else {
	    executeUnitMove(from, to);
            eventController.dispatch("WORLD_CHANGED", to);
	}

        eventController.dispatch("WORLD_CHANGED", from);

	return true;
    }

    public void endOfTurn() {
	if (inTurn == Player.RED) {
	    inTurn = Player.BLUE;
	} else {
	    inTurn = Player.RED;
	    endOfRound();
	}
        
        Object[] args = {inTurn, new Integer(getAge())};
        eventController.dispatch("TURN_ENDS", args);
    }

    private void endOfRound() {
	round++;
	incrementProductionAmount();
	produceUnits();
        eventController.dispatch("NEW_ROUND", new Integer(round));
    }

    private void incrementProductionAmount() {
	for (Position p : cities.keySet()) {
	    City old = cities.get(p);
	    cities.put(p, new CityImpl(old.getOwner(), old.getProduction(),
				       old.getProductionAmount() + 6));
	}
    }

    private void produceUnits() {
	for (Position p : cities.keySet()) {
	    City c = cities.get(p);
	    String type = c.getProduction();
	    int cost = 0;

	    if (type.equals(GameConstants.ARCHER)) {
		cost = 10;
	    } else if (type.equals(GameConstants.LEGION)) {
		cost = 15;
	    } else if (type.equals(GameConstants.SETTLER)) {
		cost = 30;
	    } 

	    if (c.getProductionAmount() >= cost) {
		Position free = getNextFreeUnitPosition(p);
		if (free != null) {
                    units.put(free, UnitImpl.create(type, c.getOwner(),
                                            round - 1));
		    cities.put(p, new CityImpl(c.getOwner(), type,
					       c.getProductionAmount() - cost));
                    eventController.dispatch("WORLD_CHANGED", free);
		}
	    }
	}
    }

    /**
     * The offsets are prioritized like this:
     * +-+-+-+
     * |8|1|2|
     * +-+-+-+
     * |7|0|3|
     * +-+-+-+
     * |6|5|4|
     * +-+-+-+
     * Where 0 is the city it is produced.
     */
    private int[] unitColOffsets = { 0, 0, 1, 1, 1, 0, -1, -1, -1 };
    private int[] unitRowOffsets = { 0, -1, -1, 0, 1, 1, 1, 0, -1 };

    private Position getNextFreeUnitPosition(Position city) {
	for (int i = 0; i<9; i++) {
	    Position p = new Position(city.getRow() + unitRowOffsets[i],
				      city.getColumn() + unitColOffsets[i]);
	    if (getUnitAt(p) == null &&
		!getTileAt(p).getTypeString().equals(GameConstants.MOUNTAINS) &&
		!getTileAt(p).getTypeString().equals(GameConstants.OCEANS)) {
		return p;
	    }
	}
	return null;
    }

    public void changeWorkForceFocusInCityAt( Position p, String balance ) {}

    public void changeProductionInCityAt( Position p, String unitType ) {
	City old = cities.get(p);
	if (old.getOwner() != inTurn)
	    return;
        cities.put(p, new CityImpl(old.getOwner(), unitType,
                    old.getProductionAmount()));
        eventController.dispatch("WORLD_CHANGED", p);
    }

    public void performUnitActionAt( Position p ) {
        units.put(p, actionStrategy.performUnitActionAt(this, p));
        eventController.dispatch("WORLD_CHANGED", p);
    }

    public Collection<City> getCities() {
        return Collections.unmodifiableCollection(cities.values());
    }

    public void addCityAt(Position p, City c) {
	cities.put(p, c);
        eventController.dispatch("WORLD_CHANGED", p);
    }

    public void addObserver(final GameObserver observer) {
        // World changed
        eventController.subscribe(new GameEventListener() {
            public void dispatch(Object o) {
                Position pos = (Position) o;
                observer.worldChangedAt(pos);
            }
            public String getType() {
                return "WORLD_CHANGED";
            }
        });
        
        // turnEnds
        eventController.subscribe(new GameEventListener() {
            public void dispatch(Object o) {
                Object[] e = (Object[]) o;
                Player nextPlayer = (Player) e[0];
                int age = (Integer) e[1];
                observer.turnEnds(nextPlayer, age);
            }
            public String getType() {
                return "TURN_ENDS";
            }
        });
        
        // tileFocusChangedAt
        eventController.subscribe(new GameEventListener() {
            public void dispatch(Object o) {
                Position pos = (Position) o;
                observer.tileFocusChangedAt(pos);
            }
            public String getType() {
                return "FOCUS_CHANGED";
            }
        });
    }

    public void setTileFocus(Position position) {
        tileFocus = position;
        eventController.dispatch("FOCUS_CHANGED", position);
    }
}
