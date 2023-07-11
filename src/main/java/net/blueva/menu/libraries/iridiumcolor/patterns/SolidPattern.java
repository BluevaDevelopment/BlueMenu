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


import net.blueva.menu.libraries.iridiumcolor.IridiumColorAPI;

import java.util.regex.Matcher;

public class SolidPattern implements Pattern {

    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<SOLID:([0-9A-Fa-f]{6})>|#\\{([0-9A-Fa-f]{6})}");

    /**
     * Applies a solid RGB color to the provided String.
     * Output might me the same as the input if this pattern is not present.
     *
     * @param string The String to which this pattern should be applied to
     * @return The new String with applied pattern
     */
    public String process(String string) {
        Matcher matcher = pattern.matcher(string);
        while (matcher.find()) {
            String color = matcher.group(1);
            if (color == null) color = matcher.group(2);

            string = string.replace(matcher.group(), IridiumColorAPI.getColor(color) + "");
        }
        return string;
    }

}
