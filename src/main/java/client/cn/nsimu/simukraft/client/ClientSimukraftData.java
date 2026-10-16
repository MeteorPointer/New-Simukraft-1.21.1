package client.cn.nsimu.simukraft.client;

import common.cn.nsimu.simukraft.city.CityPermissionLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSimukraftData {
    private static int currentDay = 1;
    private static int currentPopulation = 0;
    private static String currentCityName = "";
    private static double currentCityFunds = 0.0D;
    private static int currentCityPopulation = 0;
    private static CityPermissionLevel permissionLevel = CityPermissionLevel.CITIZEN;
    private static boolean creativeMode = false;

    private ClientSimukraftData() {
    }

    public static synchronized void setCurrentDay(int day) {
        currentDay = Math.max(1, day);
    }

    public static synchronized int getCurrentDay() {
        return currentDay;
    }

    public static synchronized void setCurrentPopulation(int population) {
        currentPopulation = Math.max(0, population);
    }

    public static synchronized int getCurrentPopulation() {
        return currentPopulation;
    }

    public static synchronized void setCurrentCityData(String cityName, double funds, int population) {
        currentCityName = cityName != null ? cityName : "";
        currentCityFunds = funds;
        currentCityPopulation = Math.max(0, population);
    }

    public static synchronized void setCurrentCityName(String cityName) {
        currentCityName = cityName != null ? cityName : "";
    }

    public static synchronized String getCurrentCityName() {
        return currentCityName;
    }

    public static synchronized void setCurrentCityFunds(double funds) {
        currentCityFunds = funds;
    }

    public static synchronized double getCurrentCityFunds() {
        return currentCityFunds;
    }

    public static synchronized void setCurrentCityPopulation(int population) {
        currentCityPopulation = Math.max(0, population);
    }

    public static synchronized int getCurrentCityPopulation() {
        return currentCityPopulation;
    }

    public static synchronized void setPermissionLevel(CityPermissionLevel level) {
        permissionLevel = level != null ? level : CityPermissionLevel.CITIZEN;
    }

    public static synchronized CityPermissionLevel getPermissionLevel() {
        return permissionLevel;
    }

    public static synchronized void setCreativeMode(boolean enabled) {
        creativeMode = enabled;
    }

    public static synchronized boolean isCreativeMode() {
        return creativeMode;
    }

    public static synchronized void resetCityData() {
        currentCityName = "";
        currentCityFunds = 0.0D;
        currentCityPopulation = 0;
    }

    public static synchronized void resetAllClientState() {
        currentDay = 1;
        currentPopulation = 0;
        creativeMode = false;
        permissionLevel = CityPermissionLevel.CITIZEN;
        resetCityData();
    }
}
