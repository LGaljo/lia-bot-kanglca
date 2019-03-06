import lia.api.*;
import lia.*;

import java.util.HashMap;
import java.util.Vector;

import static java.lang.Double.NaN;

/**
 * Initial implementation keeps picking random locations on the map
 * and sending units there. Worker units collect resources if they
 * see them while warrior units shoot if they see opponents.
 */
public class MyBot implements Bot {
    private static HashMap<Integer, OpponentInView> oiv = new HashMap<>();

    // This method is called 10 times per game second and holds current
    // game state. Use Api object to call actions on your units.
    // - GameState reference: https://docs.liagame.com/api/#gamestate
    // - Api reference:       https://docs.liagame.com/api/#api-object
    @Override
    public void update(GameState state, Api api) {
        manageSpawn(state, api);

        // We iterate through all of our units that are still alive.
        for (int i = 0; i < state.units.length; i++) {
            UnitData unit = state.units[i];

            if (unit.type == UnitType.WORKER) {
                workers(state, api, unit);
            } else {
                warriors(state, api, unit);
            }
        }

        oiv.clear();
        for (int h = 0; h < state.units.length; h++) {
            UnitData unit = state.units[h];
            for (int g = 0; g < unit.opponentsInView.length; g++) {
                oiv.put(unit.opponentsInView[g].id, unit.opponentsInView[g]);
            }
        }
    }

    private static void manageSpawn(GameState state, Api api) {
        // Calculate how many workers you currently have
        int numberOfWorkers = 0;
        for (int i = 0; i < state.units.length; i++) {
            if (state.units[i].type == UnitType.WORKER) {
                numberOfWorkers++;
            }
        }

        // If from all of your units less than 60% are workers
        // and you have enough resources, then create a new worker.
        if (numberOfWorkers / (float) state.units.length < 0.5f && numberOfWorkers < 7 && state.time < 125) {
            if (state.resources >= Constants.WORKER_PRICE) {
                api.spawnUnit(UnitType.WORKER);
            }
        }
        // Else if you can, spawn a new warrior
        else if (state.resources >= Constants.WARRIOR_PRICE) {
            api.spawnUnit(UnitType.WARRIOR);
        }
    }

    private static void workers(GameState state, Api api, UnitData unit) {
        // Poberi vir, ki ga vidis
        if (unit.resourcesInView.length > 0) {
            api.saySomething(unit.id, "I see resource");
            ResourceInView resource = unit.resourcesInView[0];
            api.navigationStart(unit.id, resource.x, resource.y);

            // Ce ne vidis vira
        } else if (unit.navigationPath.length == 0) {
            // Poglej kje je najblizja surovina, ki je ne vidis
            ResourceInView min_riv = null;
            float min_dist = 1000;

            for (int i = 0; i < state.units.length; i++) {
                UnitData teammate = state.units[i];
                for (int j = 0; i < teammate.resourcesInView.length; i++) {
                    ResourceInView resource = teammate.resourcesInView[j];
                    float dist = MathUtil.distance(resource.x, resource.y, unit.x, unit.y);
                    if (min_dist > dist) {
                        min_riv = resource;
                    }
                }
            }
            // Ce kdo vidi surovino, pojdi do nje
            if (min_riv != null) {
                api.navigationStart(unit.id, min_riv.x, min_riv.y);
            } else {
                api.saySomething(unit.id, "Have no clue");
                sendToRandomLocation(api, unit);
            }
        }
    }

    // izracunaj razdaljo med potjo strela in soigralci
    private static boolean teamfire(GameState state, Api api, UnitData unit) {
        for (int i = 0; i < state.units.length; i++) {
            UnitData teammate = state.units[i];
            if (unit.id != teammate.id && distancePointLine(unit, teammate) < 0.7f) {
                return true;
            }
        }

        return false;
    }

    private static float distancePointLine(UnitData u1, UnitData u2) {
        float angle1 = u1.orientationAngle;
        float x1 = u1.x;
        float y1 = u1.y;
        float x2 = u2.x;
        float y2 = u2.y;

        float x3 = x1 + 70;
        float y3 = (float) Math.tan(angle1) * 70;

        return (float) (Math.abs((x3 - x1) * (y1 - y2) - (x1 - x2) * (y3 - y1)) /
                Math.sqrt(Math.pow(x3 - x1, 2) + Math.pow(y3 - y1, 2)));
    }

    private static OpponentInView estimate(UnitData src, OpponentInView dest) {
        double enemy_velocity = 0.0;
        if (dest.speed == Speed.FORWARD) {
            enemy_velocity = 7.2;
        }
        double bullet_velocity = 32.0;
        double tvx = enemy_velocity * Math.cos(dest.orientationAngle);
        double tvy = enemy_velocity * Math.sin(dest.orientationAngle);

        double a = tvx*tvx + tvy*tvy - bullet_velocity*bullet_velocity;
        double b = 2 * (tvx * (dest.x - src.x) + enemy_velocity * (dest.y - src.y));
        double c = (dest.x - src.x)*(dest.x - src.x) + (dest.y - src.y)*(dest.y - src.y);

        double disc = b*b - 4 * a * c;

        double t = 0.0;
        double t1 = (-b + Math.sqrt(disc)) / (2 * a);
        double t2 = (-b - Math.sqrt(disc)) / (2 * a);

        if (disc == 0) {
            t = t1;
        } else if (t1 < 0) {
            t = t2;
        } else if (t2 < 0) {
            t = t1;
        }

        double aimX = t * tvx + dest.x;
        double aimY = t * tvy + dest.y;

        if (aimX < 0) {
            aimX = 0;
        }
        if (aimY < 0) {
            aimY = 0;
        }
        if (aimX > 98) {
            aimX = 98;
        }
        if (aimY > 175) {
            aimY = 175;
        }

        dest.x = (float)aimX;
        dest.y = (float)aimY;


        return dest;
    }

    private static void warriors(GameState state, Api api, UnitData unit) {
        // Get the first opponent that the unit sees.
        if (unit.opponentsInView.length > 0) {
            OpponentInView opponent = unit.opponentsInView[0];

            // Tukaj dodaj priblizek nasprotnikove lokacije glede na njegovo hitrost in smer
            //OpponentInView estimatedOpponent = estimate(unit, opponent);

            // Calculate the aiming angle between units orientation and the opponent. The closer
            // the angle is to 0 the closer is the unit aiming towards the opponent.
            float aimAngle = MathUtil.angleBetweenUnitAndPoint(unit, opponent.x, opponent.y);

            // Stop the unit.
            //api.setSpeed(unit.id, Speed.NONE);

            // Based on the aiming angle turn towards the opponent.
            if (aimAngle < 0) {
                api.setRotation(unit.id, Rotation.RIGHT);
            } else {
                api.setRotation(unit.id, Rotation.LEFT);
            }

            if (!teamfire(state, api, unit)) {
                api.shoot(unit.id);
            }

        } else if (unit.navigationPath.length == 0) {
            // Pojdi do najblizjega nasprotnika
            OpponentInView min_oiv = null;
            float min_dist = 10000;
            for (int i = 0; i < state.units.length; i++) {
                UnitData teammate = state.units[i];
                for (int j = 0; i < teammate.opponentsInView.length; i++) {
                    OpponentInView opponent = teammate.opponentsInView[j];
                    if (min_dist > MathUtil.distance(opponent.x, opponent.y, unit.x, unit.y)) {
                        min_oiv = opponent;
                    }
                }
            }
            if (min_oiv != null) {
                api.navigationStart(unit.id, min_oiv.x, min_oiv.y);
            } else {
                sendToRandomLocation(api, unit);
            }
        }
    }

    private static void sendToRandomLocation(Api api, UnitData u) {
        // Generate new x and y until you get a position on the map
        // where there is no obstacle. Then move the unit there.
        while (true) {
            int x = (int) (Math.random() * Constants.MAP_WIDTH);
            int y = (int) (Math.random() * Constants.MAP_HEIGHT);

            // Map is a 2D array of booleans. If map[x][y] equals false it means that
            // at (x,y) there is no obstacle and we can safely move our unit there.
            if (!Constants.MAP[x][y]) {
                api.navigationStart(u.id, x, y);
                break;
            }
        }
    }

    // Connects your bot to Lia game engine, don't change it.
    public static void main(String[] args) throws Exception {
        NetworkingClient.connectNew(args, new MyBot());
    }
}
