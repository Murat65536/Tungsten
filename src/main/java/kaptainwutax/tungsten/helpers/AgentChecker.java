package kaptainwutax.tungsten.helpers;

import kaptainwutax.tungsten.simulation.SimulatedPlayer;

/**
 * Helper class to check agent for certain conditions.
 */
public class AgentChecker {

    /**
     * Checks if agent is on ground and has less velocity then a given value.
     *
     * @param agent       Simulated player to be checked
     * @param minVelocity Player needs to have less velocity then this to be considered stationary
     * @return true if agent is on ground and has less velocity then given value.
     */
    public static boolean isAgentStationary(SimulatedPlayer agent, double minVelocity) {
        return agent.velX < minVelocity && agent.velX > -minVelocity &&
                agent.velZ < minVelocity && agent.velZ > -minVelocity &&
                agent.onGround;
    }


}
