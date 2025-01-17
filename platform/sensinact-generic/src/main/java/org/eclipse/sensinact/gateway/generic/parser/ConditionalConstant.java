/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.generic.parser;

import org.eclipse.sensinact.gateway.common.bundle.Mediator;
import org.eclipse.sensinact.gateway.common.constraint.Fixed;
import org.eclipse.sensinact.gateway.common.primitive.JSONable;
import org.eclipse.sensinact.gateway.core.method.trigger.AccessMethodTrigger;
import org.eclipse.sensinact.gateway.util.JSONUtils;
import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers a constant value with a set of {@link ConstraintDefinition}s 
 *
 * @author <a href="mailto:christophe.munilla@cea.fr">Christophe Munilla</a>
 */
@XmlEscaped(value = {"constraints"})
public class ConditionalConstant extends ResolvedNameTypeValueDefinition implements ConstrainableDefinition, JSONable {
    
    private List<ConstraintDefinition> constraints;

    /**
     * Constructor
     */
    public ConditionalConstant(Mediator mediator, Attributes atts) {
        super(mediator, atts);
        this.constraints = new ArrayList<ConstraintDefinition>();
    }

    /**
     * @inheritDoc
     * @see ConstrainableDefinition#
     * addConstraint(ConstraintDefinition)
     */
    public void addConstraint(ConstraintDefinition constraint) {
        this.constraints.add(constraint);
    }

    /**
     * Returns true if the set of {@link ConstraintDefinition}s of this
     * ConditionalConstant is empty or contains only one fixed {@link
     * ConstraintDefinition}; returns false otherwise
     *
     * @return <ul>
     * <li>
     * true if the set of {@link ConstraintDefinition}s
     * is empty or contains only one fixed one</li>
     * <li>
     * false otherwise
     * </li>
     * </ul>
     */
    public boolean isUnconditional() {
        int length = this.constraints.size();
        return (length == 0 || (length == 1 
        && Fixed.class.isAssignableFrom(this.constraints.get(0).getClass())));
    }

    /**
     * @inheritDoc
     * @see JSONable#getJSON()
     */
    @Override
    public String getJSON() {
    	TypeValuePair pair = super.getTypeValuePair();
        String constant = JSONUtils.toJSONFormat(pair.value);

        StringBuilder builder = new StringBuilder();

        if (this.isUnconditional()) {
            return constant;

        } else {
            builder.append(JSONUtils.OPEN_BRACE);
            builder.append(JSONUtils.QUOTE);
            builder.append(AccessMethodTrigger.TRIGGER_CONSTANT_KEY);
            builder.append(JSONUtils.QUOTE);
            builder.append(JSONUtils.COLON);
            builder.append(constant);
            builder.append(JSONUtils.COMMA);
            builder.append(JSONUtils.QUOTE);
            builder.append(AccessMethodTrigger.TRIGGER_CONSTRAINT_KEY);
            builder.append(JSONUtils.QUOTE);
            builder.append(JSONUtils.COLON);
            builder.append(JSONUtils.OPEN_BRACKET);
            int index = 0;
            for (; index < this.constraints.size(); index++) {
                builder.append(index > 0 ? JSONUtils.COMMA : JSONUtils.EMPTY);
                ConstraintDefinition def = this.constraints.get(index);
                def.setType(new TypeDefinition<Class<?>>(pair.type));
                builder.append(def.getJSON());
            }
            builder.append(JSONUtils.CLOSE_BRACKET);
            builder.append(JSONUtils.CLOSE_BRACE);
        }
        return builder.toString();
    }
}
