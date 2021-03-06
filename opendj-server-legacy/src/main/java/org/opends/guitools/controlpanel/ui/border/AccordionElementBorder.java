/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.border.Border;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/** The border specific to the accordion element. */
public class AccordionElementBorder implements Border
{
  private Insets insets = new Insets(1, 1, 1, 1);

  /** Default constructor. */
  public AccordionElementBorder() {
  }

  /** {@inheritDoc} */
  public Insets getBorderInsets(Component c) {
    return insets;
  }

  /** {@inheritDoc} */
  public boolean isBorderOpaque() {
    return true;
  }

  /** {@inheritDoc} */
  public void paintBorder(Component c, Graphics g, int x, int y, int width,
      int height) {
    g.setColor(ColorAndFontConstants.topAccordionBorderColor);
    // render highlight at top
    g.drawLine(x, y, x + width - 1, y);
    // render left
    g.drawLine(x, y, x, y + height - 1);
    // render right
    g.drawLine(x + width - 1, y, x + width - 1, y + height - 1);
    // render shadow on bottom
    g.setColor(ColorAndFontConstants.defaultBorderColor);
    g.drawLine(x, y + height - 1, x + width - 1, y + height - 1);
  }
}
