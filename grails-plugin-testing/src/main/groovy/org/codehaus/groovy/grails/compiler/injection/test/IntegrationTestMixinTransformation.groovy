/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.groovy.grails.compiler.injection.test

import grails.test.mixin.integration.Integration
import grails.test.mixin.integration.IntegrationTestMixin
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * An AST transformation that automatically applies the IntegrationTestMixin to integration tests
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class IntegrationTestMixinTransformation implements ASTTransformation {

    public static final String OBJECT_CLASS = "java.lang.Object";
    private static final ClassNode MY_TYPE = new ClassNode(Integration);

    @Override
    void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        final node = astNodes[0]
        final parent = astNodes[1]
        if (!(node instanceof AnnotationNode) || !(parent instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: ${node.getClass()} / ${parent.getClass()}");
        }

        AnnotationNode annotationNode = (AnnotationNode) astNodes[0];
        if (!MY_TYPE.equals(annotationNode.getClassNode()) || !(parent instanceof ClassNode)) {
            return;
        }

        ClassNode classNode = (ClassNode) parent;
        ListExpression listExpression = new ListExpression()
        listExpression.addExpression(new ClassExpression(new ClassNode(IntegrationTestMixin).getPlainNodeReference()))
        if (isApplicableTo(classNode)) {
            new TestMixinTransformation().weaveMixinsIntoClass(classNode, listExpression)
        }
    }

    private static boolean isApplicableTo(ClassNode classNode) {
        // isInterface covers "interface", "trait" and "annotation"
        // we could also identify "annotations" among them,
        // but there is no special treatment needed here
        return !classNode.isInterface() &&
                !isInnerClass(classNode) &&
                !isSubclassOf(classNode, "grails.test.spock.IntegrationSpec")
    }

    private static boolean isInnerClass(ClassNode classNode) {
        return classNode.getOuterClass() != null
    }

    private static boolean isSubclassOf(ClassNode classNode, String testType) {
        ClassNode currentSuper = classNode.getSuperClass();
        while (currentSuper != null && !currentSuper.getName().equals(OBJECT_CLASS)) {
            if (currentSuper.getName().equals(testType)) return true;
            currentSuper = currentSuper.getSuperClass();
        }
        return false;
    }
}
