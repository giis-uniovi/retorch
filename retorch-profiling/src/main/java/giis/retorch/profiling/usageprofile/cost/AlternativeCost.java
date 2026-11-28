/*
 * Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
 * All intellectual property rights on this file belong to GIIS.
 */
package giis.retorch.profiling.usageprofile.cost;

import giis.retorch.profiling.usageprofile.dto.AlternativeDTO;

import java.util.List;

/**
 * One way of costing the measured execution (VMs, managed containers, browser services). {@link CostCalculator} runs the
 * strategies and sorts what they return, so a new kind of alternative is a new strategy, not a change of the calculator.
 */
interface AlternativeCost {

    /**
     * The alternatives of this kind. {@code existing} are the alternatives already calculated by the previous
     * strategies, sorted by total (the browser services build on the cheapest of them). Problems are added to
     * {@code context.warnings}.
     */
    List<AlternativeDTO> calculate(CostContext context, List<AlternativeDTO> existing);
}
