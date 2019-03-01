import lia.api.*;
import lia.*;

import java.util.HashMap;

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
        if (numberOfWorkers / (float) state.units.length < 0.5f && numberOfWorkers < 7 && state.time < 120) {
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
            if (unit.id != teammate.id && distancePointLine(unit, teammate) < 1.0) {
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

        float x3 = x1 + 100;
        float y3 = (float) Math.tan(angle1) * 100;

        return (float) (Math.abs((x3 - x1) * (y1 - y2) - (x1 - x2) * (y3 - y1)) /
                Math.sqrt(Math.pow(x3 - x1, 2) + Math.pow(y3 - y1, 2)));
    }
/*
    private static float estimate(UnitData unit, OpponentInView opponent) {
        double x = -(unit.x - opponent.x);
        double y = (unit.y - opponent.y);
        double v = 32; //m/s hitrost izstrelka
        double g = 7.2; //m/s hitrost tarce
        double sqrt = (v*v*v*v) - (g*(g*(x*x) + 2*y*(v*v)));
        sqrt = Math.sqrt(sqrt);
        float angleInRadians = (float)Math.atan(((v*v) + sqrt)/(g*x));

        return angleInRadians;
    }

    private static OpponentInView estimateTheFuture(UnitData unit, OpponentInView opponent) {
        OpponentInView previous = oiv.get(opponent.id);

        if (previous == null) {
            return opponent;
        }

        // Razdalja med tvojo enoto in nasprotnikom
        float dist = MathUtil.distance(opponent.x, opponent.y, unit.x, unit.y);

        // Cas potovanja izstrelka med tvojo enoto in nasprotnikom
        float time = dist / 32;

        float AxX = previous.x - opponent.x;
        float AxY = previous.y - opponent.y;

        // Lokaciji pristejem razdaljo odvisno od casa katerega bo metek potreboval do nasprotnika in njegove hitrosti
        float x = (int) (opponent.x + AxX * time * 7.2) * -1;
        float y = (int) (opponent.y + AxY * time * 7.2);
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x > 175) {
            x = 175;
        }
        if (y > 98) {
            y = 98;
        }

        opponent.x = x;
        opponent.y = y;

        return opponent;
    }
*/

    private static double solX = 0.0;
    private static double solY = 0.0;

    private static OpponentInView estimate(UnitData src, OpponentInView dst) {
        double enemy_velocity = 7.2;
        double bullet_velocity = 32.0;
        double tx = dst.x - src.x;
        double ty = dst.y - src.y;
        double tvx = enemy_velocity * Math.cos(dst.orientationAngle);
        double tvy = enemy_velocity * Math.sin(dst.orientationAngle);

        //float distance = MathUtil.distance(src.x, src.y, dst.x, dst.y);

        // Get quadratic equation components
        double a = tvx * tvx + tvy * tvy - bullet_velocity * bullet_velocity;
        double b = 2 * (tvx * tx + tvy * ty);
        double c = tx * tx + ty * ty;

        // Solve quadratic
        quad(a, b, c); // See quad(), below

        // Find smallest positive solution
        if (solX != NaN && solY != NaN) {
            System.out.println("Got it");
            double t0 = solX, t1 = solY;
            double t = Math.min(t0, t1);
            if (t < 0) {
                t = Math.max(t0, t1);
                System.out.println("here t:" + t);
            }
            if (t > 0) {
                dst.x = (float)(dst.x + tvx * t);
                dst.y = (float)(dst.y + tvy * t);
            }
        }

        return dst;
    }

    private static void quad(double a, double b, double c) {
        solX = 0;
        solY = 0;

        if (Math.abs(a) < 1e-6) {
            if (Math.abs(b) < 1e-6) {
                if (Math.abs(c) < 1e-6) {
                    solX = 0.0;
                    solY = 0.0;
                } else {
                    solX = NaN;
                    solY = NaN;
                }
            } else {
                solX = -c / b;
                solY = -c / b;
            }
        } else {
            double disc = b * b - 4 * a * c;
            if (disc >= 0) {
                disc = Math.sqrt(disc);
                a = 2 * a;
                solX = (-b - disc) / a;
                solY = (-b + disc) / a;
            }
        }
    }

    private static void warriors(GameState state, Api api, UnitData unit) {
        // Get the first opponent that the unit sees.
        if (unit.opponentsInView.length > 0) {
            OpponentInView opponent = unit.opponentsInView[0];

            // TODO: Tukaj dodaj priblizek nasprotnikove lokacije glede na njegovo hitrost in smer
            OpponentInView estimatedOpponent = estimate(unit, opponent);

            System.out.println("now: x:" + opponent.x + " y:" + opponent.y);
            System.out.println("est: x:" + estimatedOpponent.x + " y:" + estimatedOpponent.y);

            if (!teamfire(state, api, unit) && MathUtil.angleBetweenUnitAndPoint(unit, opponent.x, opponent.y) < 5f) {
                api.shoot(unit.id);
            }

            api.navigationStart(unit.id, opponent.x, opponent.y);
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

    /*
        private static int w = 0;
        private static void firstTimeLoc(Api api, UnitData u) {
            // Generate new x and y until you get a position on the map
            // where there is no obstacle. Then move the unit there.
            int x = 0;
            int y = 0;
            switch (w) {
                case 0:
                    x = (int) (Math.random() * (Constants.MAP_WIDTH / 2));
                    y = (int) (Math.random() * (Constants.MAP_HEIGHT / 2));
                    break;
                case 1:
                    x = (int) ((Math.random() * Constants.MAP_WIDTH) + Constants.MAP_WIDTH / 2 - 1);
                    y = (int) ((Math.random() * Constants.MAP_HEIGHT) + Constants.MAP_HEIGHT / 2 - 1);
                    break;
                case 2:
                    x = (int) (Math.random() * (Constants.MAP_WIDTH));
                    y = (int) ((Math.random() * (Constants.MAP_HEIGHT)) + Constants.MAP_HEIGHT / 2 - 1);
                    break;
                default:
                    x = (int) (Math.random() * Constants.MAP_WIDTH);
                    y = (int) (Math.random() * Constants.MAP_HEIGHT);
                    break;
            }
            w++;

            while (true) {
                // Map is a 2D array of booleans. If map[x][y] equals false it means that
                // at (x,y) there is no obstacle and we can safely move our unit there.
                if (!Constants.MAP[x][y]) {
                    api.navigationStart(u.id, x, y);
                    break;
                }
            }
        }
    */
    // Connects your bot to Lia game engine, don't change it.
    public static void main(String[] args) throws Exception {
        NetworkingClient.connectNew(args, new MyBot());
    }
}
