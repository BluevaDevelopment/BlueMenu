/*
 * This code uses part of the code from the IridiumColorAPI project, developed by Iridium-Development.
 * You can find more information about the project at https://github.com/Iridium-Development/IridiumColorAPI.
 *
 * Copyright (c) 2023, Iridium-Development
 * This software is subject to the GNU General Public License, Version 3.0. You may obtain a copy of the license at
 * https://www.gnu.org/licenses/gpl-3.0.en.html.
 *
 */
package net.blueva.menu.libraries.iridiumcolor.patterns;

/**
 * Represents a color pattern which can be applied to a String.
 */
public interface Pattern {

    /**
     * Applies this pattern to the provided String.
     * Output might me the same as the input if this pattern is not present.
     *
     * @param string The String to which this pattern should be applied to
     * @return The new String with applied pattern
     */
    String process(String string);

}
