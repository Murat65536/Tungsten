package kaptainwutax.tungsten.helpers;

import kaptainwutax.tungsten.simulation.SimulatedPlayerState;

/**
 * Helper class to check agent for certain conditions.
 */
public class AgentChecker {

    /**
     * Checks if agent is on ground and has less velocity then a given value.
     *
     * @param state       Agent state to be checked
     * @param minVelocity Agent needs to have less velocity then this to be considered stationary
     * @return true if agent is on ground and has less velocity then given value.
     */
    public static boolean isAgentStationary(SimulatedPlayerState state, double minVelocity) {
        return state.getVelocity().x < minVelocity && state.getVelocity().x > -minVelocity &&
                state.getVelocity().z < minVelocity && state.getVelocity().z > -minVelocity &&
                state.isOnGround();
    }


}