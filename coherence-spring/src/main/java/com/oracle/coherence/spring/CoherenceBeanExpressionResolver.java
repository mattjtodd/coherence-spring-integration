/*
 * File: CoherenceBeanExpressionResolver.java
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * The contents of this file are subject to the terms and conditions of 
 * the Common Development and Distribution License 1.0 (the "License").
 *
 * You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the License by consulting the LICENSE.txt file
 * distributed with this file, or by consulting https://oss.oracle.com/licenses/CDDL
 *
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file LICENSE.txt.
 *
 * MODIFICATIONS:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 */

package com.oracle.coherence.spring;

import com.tangosol.coherence.config.ParameterMacroExpressionParser;

import com.tangosol.config.expression.NullParameterResolver;
import com.tangosol.config.expression.ParameterResolver;

import org.springframework.context.expression.StandardBeanExpressionResolver;

import org.springframework.core.convert.TypeDescriptor;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.ParserContext;

import org.springframework.expression.common.TemplateAwareExpressionParser;

import org.springframework.expression.spel.standard.SpelExpressionParser;

import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * A {@link org.springframework.beans.factory.config.BeanExpressionResolver}
 * implementation that bridges Coherence configuration concepts with Spring
 * configuration concepts. Ultimately this class and it's children support
 * the ability to reference Coherence parameter macros within a spring
 * application context file.
 * <p>
 * Copyright (c) 2013. All Rights Reserved. Oracle Corporation.<br>
 * Oracle is a registered trademark of Oracle Corporation and/or its affiliates.
 *
 * @author Harvey Raja
 *
 * @see SpringNamespaceHandler
 */
public class CoherenceBeanExpressionResolver extends StandardBeanExpressionResolver
{
    // ----- data members ---------------------------------------------------

    /**
     * A thread local {@link ParameterResolver} used to hold the context
     * sensitive {@link ParameterResolver} based on the bean's reference in
     * the cache configuration file.
     * <p>
     * This member is thread local to avoid a context bleeding when used by
     * multiple threads such as when a backing map is created in parallel to
     * a service being processed.
     */
    private final ThreadLocal<ParameterResolver> m_tlResolver = new ThreadLocal<ParameterResolver>()
    {
        @Override
        protected ParameterResolver initialValue()
        {
            return new NullParameterResolver();
        }
    };


    // ----- constructors ---------------------------------------------------

    /**
     * Creates a CoherenceBeanExpressionResolver instance.
     */
    public CoherenceBeanExpressionResolver(com.tangosol.config.expression.ExpressionParser exprParser)
    {
        super();
        setExpressionParser(new CoherenceExpressionParser(exprParser));
    }


    // ----- StandardBeanExpressionResolver methods -------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    protected void customizeEvaluationContext(StandardEvaluationContext evalContext)
    {
        evalContext.setVariable("resolver", getResolver());
    }


    /**
     * Returns a thread local instance of a {@link ParameterResolver}.
     *
     * @return a context sensitive {@link ParameterResolver}
     */
    public ParameterResolver getResolver()
    {
        return m_tlResolver.get();
    }


    /**
     * Set the thread local {@link ParameterResolver}.
     *
     * @param resolver  resolver to use to determine variables
     */
    public void setParameterResolver(ParameterResolver resolver)
    {
        m_tlResolver.set(resolver);
    }


    /**
     * A CoherenceExpressionParser determines whether a string expression can
     * be processed by a Coherence {@link ParameterMacroExpressionParser}.
     * If not it will be delegated to the {@link ExpressionParser} this class
     * is initialized with.
     */
    private class CoherenceExpressionParser extends TemplateAwareExpressionParser
    {
        // ----- data members -----------------------------------------------

        /**
         * The {@link com.tangosol.config.expression.ExpressionParser}
         * used to parse the string expression.
         */
        private com.tangosol.config.expression.ExpressionParser m_exprParserCoh;

        /**
         * The Spring {@link ExpressionParser} used when the Coherence
         * {@link com.tangosol.config.expression.ExpressionParser} is not
         * applicable.
         */
        private ExpressionParser m_exprParserSpring;


        // ----- constructors -----------------------------------------------

        /**
         * Creates a CoherenceExpressionParser with a
         * {@link SpelExpressionParser}.
         */
        public CoherenceExpressionParser(com.tangosol.config.expression.ExpressionParser exprParserCoh)
        {
            this(exprParserCoh, new SpelExpressionParser());
        }


        /**
         * Creates a CoherenceExpressionParser with the provided
         * {@link ExpressionParser}.
         */
        public CoherenceExpressionParser(com.tangosol.config.expression.ExpressionParser exprParserCoh,
                                         ExpressionParser                                exprParserSpring)
        {
            m_exprParserCoh    = exprParserCoh;
            m_exprParserSpring = exprParserSpring;
        }


        // ----- TemplateAwareExpressionParser methods ----------------------

        /**
         * {@inheritDoc}
         */
        @Override
        protected Expression doParseExpression(String        sExpression,
                                               ParserContext context) throws ParseException
        {
            sExpression = sExpression == null ? "" : sExpression.trim();

            return new DelegatingExpression("{" + sExpression + "}",
                                            m_exprParserSpring.parseExpression("#{" + sExpression + "}", context));
        }


        /**
         * An {@link Expression} implementation that delegates expression
         * evaluation to a Coherence
         * {@link com.tangosol.config.expression.ExpressionParser}.
         */
        private class DelegatingExpression implements Expression
        {
            // ----- data members -------------------------------------------

            /**
             * The string expression used to derive some object.
             */
            private String m_sExpression;

            /**
             * The original spring expression evaluated iff we are unsuccessful
             * in evaluation.
             */
            private Expression m_exprSpring;


            // ----- constructors -------------------------------------------

            /**
             * Creates DelegatingExpression instance witch the Coherence
             * {@link com.tangosol.config.expression.ExpressionParser} and
             * teh string expression.
             *
             * @param sExpression  the string expression to use
             */
            public DelegatingExpression(String     sExpression,
                                        Expression exprSpring)
            {
                m_sExpression = sExpression;
                m_exprSpring  = exprSpring;
            }


            // ----- Expression methods -------------------------------------

            /**
             * {@inheritDoc}
             */
            @Override
            public Object getValue() throws EvaluationException
            {
                Object oValue = evaluate(Object.class);

                if (oValue == null)
                {
                    oValue = m_exprSpring.getValue();
                }

                return oValue;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public Object getValue(Object rootObject) throws EvaluationException
            {
                Object oValue = evaluate(Object.class);

                if (oValue == null)
                {
                    oValue = m_exprSpring.getValue(rootObject);
                }

                return oValue;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public <T> T getValue(Class<T> desiredResultType) throws EvaluationException
            {
                T value = evaluate(desiredResultType);

                if (value == null)
                {
                    value = m_exprSpring.getValue(desiredResultType);
                }

                return value;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public <T> T getValue(Object   rootObject,
                                  Class<T> desiredResultType) throws EvaluationException
            {
                T value = evaluate(desiredResultType);

                if (value == null)
                {
                    value = m_exprSpring.getValue(rootObject, desiredResultType);
                }

                return value;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public Object getValue(EvaluationContext context) throws EvaluationException
            {
                Object oValue = evaluate(Object.class, getParamResolver(context));

                if (oValue == null)
                {
                    oValue = m_exprSpring.getValue(context);
                }

                return oValue;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public Object getValue(EvaluationContext context,
                                   Object            rootObject) throws EvaluationException
            {
                Object oValue = evaluate(Object.class, getParamResolver(context));

                if (oValue == null)
                {
                    oValue = m_exprSpring.getValue(context, rootObject);
                }

                return oValue;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public <T> T getValue(EvaluationContext context,
                                  Class<T>          desiredResultType) throws EvaluationException
            {
                T value = evaluate(desiredResultType, getParamResolver(context));

                if (value == null)
                {
                    value = m_exprSpring.getValue(context, desiredResultType);
                }

                return value;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public <T> T getValue(EvaluationContext context,
                                  Object            rootObject,
                                  Class<T>          desiredResultType) throws EvaluationException
            {
                T value = evaluate(desiredResultType, getParamResolver(context));

                if (value == null)
                {
                    value = m_exprSpring.getValue(context, rootObject, desiredResultType);
                }

                return value;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public Class getValueType() throws EvaluationException
            {
                return Object.class;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public Class getValueType(Object rootObject) throws EvaluationException
            {
                return getValueType();
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public Class getValueType(EvaluationContext context) throws EvaluationException
            {
                return getValueType();
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public Class getValueType(EvaluationContext context,
                                      Object            rootObject) throws EvaluationException
            {
                return getValueType();
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public TypeDescriptor getValueTypeDescriptor() throws EvaluationException
            {
                return TypeDescriptor.valueOf(Object.class);
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public TypeDescriptor getValueTypeDescriptor(Object rootObject) throws EvaluationException
            {
                return getValueTypeDescriptor();
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public TypeDescriptor getValueTypeDescriptor(EvaluationContext context) throws EvaluationException
            {
                return getValueTypeDescriptor();
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public TypeDescriptor getValueTypeDescriptor(EvaluationContext context,
                                                         Object            rootObject) throws EvaluationException
            {
                return getValueTypeDescriptor();
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isWritable(EvaluationContext context) throws EvaluationException
            {
                return false;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isWritable(EvaluationContext context,
                                      Object            rootObject) throws EvaluationException
            {
                return false;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isWritable(Object rootObject) throws EvaluationException
            {
                return false;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public void setValue(EvaluationContext context,
                                 Object            value) throws EvaluationException
            {
                return;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public void setValue(Object rootObject,
                                 Object value) throws EvaluationException
            {
                return;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public void setValue(EvaluationContext context,
                                 Object            rootObject,
                                 Object            value) throws EvaluationException
            {
                return;
            }


            /**
             * {@inheritDoc}
             */
            @Override
            public String getExpressionString()
            {
                return m_sExpression;
            }


            // ----- helpers ------------------------------------------------

            /**
             * Return the thread local cached resolver and if not present
             * use the provided {@link EvaluationContext} to determine whether
             * a {@link ParameterResolver} is accessible, with the well known
             * reference {@literal "resolver"}. The thread local cached resolver
             * takes precedence as it is considered to be more correct than
             * the set-once EvaluationContext resolver.
             *
             * @param ctx  the context that holds the appropriate
             *             {@link ParameterResolver}
             * @return either a {@link ParameterResolver} on the context or
             *         a thread local version
             */
            protected ParameterResolver getParamResolver(EvaluationContext ctx)
            {
                ParameterResolver resolver = getResolver();

                if (resolver == null)
                {
                    Object oResolver = ctx.lookupVariable("resolver");

                    if (oResolver instanceof ParameterResolver)
                    {
                        resolver = (ParameterResolver) oResolver;
                    }
                }

                return resolver;
            }


            /**
             * Based on the known string expression create an instance of the
             * destined type.
             *
             * @param clzDestined  the type of object parsing the string
             *                     should resolve
             * @param <T>          the instance type to return
             *
             * @return an instance of type {@link T} created from resolving
             *         the string expression
             */
            protected <T> T evaluate(Class<T> clzDestined)
            {
                return evaluate(clzDestined, getResolver());
            }


            /**
             * Based on the known string expression and the
             * {@link ParameterResolver} create an instance of the destined
             * type.
             *
             * @param clzDestined  the type of object parsing the string
             *                     should resolve
             *        resolver     the parameter resolver used by the
             *                     expression
             * @param <T>          the instance type to return
             *
             * @return an instance of type {@link T} created from resolving
             *         the string expression
             */
            protected <T> T evaluate(Class<T>          clzDestined,
                                     ParameterResolver resolver)
            {
                try
                {
                    return m_exprParserCoh.parse(m_sExpression, clzDestined).evaluate(resolver);
                }
                catch (Throwable t)
                {
                }

                return null;
            }
        }
    }
}
