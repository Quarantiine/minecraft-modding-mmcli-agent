package com.example.client.renderer;

import com.example.patrol.PatrolRoute;
import com.example.patrol.PatrolRouteManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side tracker storing active in-world patrol routes received from the server.
 * Allows 3D holographic wireframe and trajectory rendering while holding the Command Scepter.
 */
public class ClientPatrolRouteTracker {

	private static final Map<Integer, PatrolRoute> ACTIVE_ROUTES = new ConcurrentHashMap<>();

	public static void setRoutes(List<PatrolRoute> routes) {
		if (routes == null) return;
		ACTIVE_ROUTES.clear();
		for (PatrolRoute r : routes) {
			ACTIVE_ROUTES.put(r.routeId(), r);
		}
	}

	public static PatrolRoute getRoute(int routeId) {
		return ACTIVE_ROUTES.computeIfAbsent(routeId, PatrolRoute::createDefault);
	}

	public static Collection<PatrolRoute> getAllRoutes() {
		return ACTIVE_ROUTES.values();
	}

	public static void updateRoute(PatrolRoute route) {
		if (route != null) {
			ACTIVE_ROUTES.put(route.routeId(), route);
		}
	}

	public static boolean isAnyWaypoint(net.minecraft.util.math.BlockPos pos) {
		if (pos == null) return false;
		for (PatrolRoute r : ACTIVE_ROUTES.values()) {
			if (r != null && r.waypoints().contains(pos)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAnyWaypoint(net.minecraft.util.math.BlockPos... positions) {
		if (positions == null) return false;
		for (net.minecraft.util.math.BlockPos p : positions) {
			if (isAnyWaypoint(p)) return true;
		}
		return false;
	}

	public static PatrolRouteManager.WaypointMatch findWaypoint(net.minecraft.util.math.BlockPos... candidatePositions) {
		if (candidatePositions == null) return null;
		for (PatrolRoute r : ACTIVE_ROUTES.values()) {
			if (r == null) continue;
			for (net.minecraft.util.math.BlockPos cand : candidatePositions) {
				if (cand != null && r.waypoints().contains(cand)) {
					return new PatrolRouteManager.WaypointMatch(r.routeId(), cand, r);
				}
			}
		}
		return null;
	}

	public static void clear() {
		ACTIVE_ROUTES.clear();
	}
}
