/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.sidguptajhs.jenkins;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static me.sidguptajhs.jenkins.LoggerStep.LogLevel.fromString;

public final class LoggerStep extends Step {

    private final String message;
    private final String label;
    private final LogLevel logLevel;
    private boolean skipUiColoring = false;

    /* A {@link DataBoundConstructor} constructor is required for mandatory properties. Optional
       This is the entrypoint of the step that is called via the jenkins.logger() method
       Usage should be of the form:

       jenkins.logger(message: 'hello', label: 'a label', logLevel: 'INFO')
       jenkins.logger(messageEncoded: 'aGVsbG8K', label: 'a label', logLevel: 'INFO')
       jenkins.logger(messageEncoded: 'aGVsbG8K', labelEncoded: 'YSBsYWJlbAo=', logLevel: 'INFO')
       jenkins.logger(message: 'failure details .....', label: 'Failure detected:', logLevel: 'ERROR')
       jenkins.logger(message: 'failure details .....', label: 'Failure detected:', logLevel: 'ERROR')
       jenkins.logger(message: 'failure details .....', label: 'Failure detected:', logLevel: 'ERROR', skipUiColoring: true)
     */
    @DataBoundConstructor
    public LoggerStep(String message, String logLevel, String labelEncoded, String label, boolean skipUiColoring) {
        if (message == null) {
            throw new IllegalArgumentException("message field must be defined");
        }
        this.message = message;

        // Set Label from label or encoded value. Encoded values are useful not here but rather in DescriptorImpl where all string args containing env vars are set to NULL
        if (label == null) {
            if (labelEncoded == null) {
                throw new IllegalArgumentException("label or labelEncoded field must be defined");
            }
            this.label = new String(Base64.getDecoder().decode(labelEncoded), StandardCharsets.UTF_8);
        } else {
            this.label = label;
        }

        this.logLevel = fromString(logLevel);
        this.skipUiColoring = skipUiColoring;
    }


    @DataBoundSetter
    public void setSkipUiColoring(boolean skipUiColoring) {
        this.skipUiColoring = skipUiColoring;
    }


    @Override
    public StepExecution start(StepContext context) throws Exception {

        // This is the light grey colored item shown in BlueOcean UI on the right
        // THis part is also shown in very light color in the Jenkins regular console output
        Objects.requireNonNull(context.get(FlowNode.class)).addAction(new LabelAction(logLevel.name()));

        return new Execution(message, label, logLevel, skipUiColoring, context);
    }


    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        // Defines the step name that is exposed to the jenkins ecosystem.
        // This should return the method name in the jenkins.<method_name> call
        @Override
        public String getFunctionName() {
            return "logger";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Logger";
        }

        // This is the dark black colored item shown in BlueOcean UI.
        // As pe issue [JENKINS-53649], if the message contains an environment variable value,
        // Jenkins decides to set all namedArgs to null apparently trying to prevent security breaches
        // Encoded values are useful here where all string args containing env vars are set to NULL
        @CheckForNull
        @Override
        public String argumentsToString(@Nonnull Map<String, Object> namedArgs) {
            if (namedArgs.containsKey("labelEncoded") && namedArgs.get("labelEncoded") != null) {
                return new String(Base64.getDecoder().decode(namedArgs.get("labelEncoded").toString()), StandardCharsets.UTF_8);
            }
            return namedArgs.getOrDefault("label", "Click for details").toString();
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }

    public static class Execution extends SynchronousStepExecution<String> {
        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private transient final String message;

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private final transient String label;

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private transient final LogLevel logLevel;

        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
        private final boolean skipUiColoring;

        Execution(String message, String label, LogLevel logLevel, boolean skipUiColoring, StepContext context) {
            super(context);
            this.message = message;
            this.label = label;
            this.logLevel = logLevel;
            this.skipUiColoring = skipUiColoring;
        }

        private static final long serialVersionUID = 1L;

        @Override
        public String run() throws Exception {

            // This is the printer for content shown inside the bock when you click the arrow on the left in the BlueOcean UI
            // THis is also a part of standard jenkins logs
            PrintStream consolePrinter = Objects.requireNonNull(getContext().get(TaskListener.class)).getLogger();
            // Fail the step/stage/job according to what was asked by the user
            if (!skipUiColoring) {
                FlowNode flowNode = Objects.requireNonNull(getContext().get(FlowNode.class));
                switch (logLevel) {
                    case WARN:
                        // If flow node already has a warning action or error action, then no need to re-do
                        flowNode.addOrReplaceAction(new WarningAction(Result.UNSTABLE).withMessage(label));
                        break;
                    case ERROR:
                        flowNode.addOrReplaceAction(new WarningAction(Result.FAILURE).withMessage(label));
                        break;
                    case FATAL:
                        flowNode.addOrReplaceAction(new WarningAction(Result.FAILURE).withMessage(label));
                        Objects.requireNonNull(getContext().get(Run.class)).setResult(Result.FAILURE);
                        break;
                    default:
                }
            }

            if (label.length() > 0) {
                consolePrinter.println("[" + logLevel + "] " + label);
            }

            if (message.length() > 0) {
                String[] lines = message.split("\n");
                for (String line : lines) {
                    consolePrinter.println("[" + logLevel + "] " + line);
                }
            }
            return Objects.requireNonNull(getContext().get(FlowNode.class)).getUrl();
        }
    }


    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

        static LoggerStep.LogLevel fromString(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (Exception ignored) {
            }
            return defaultValue();
        }

        public static LoggerStep.LogLevel defaultValue() {
            return INFO;
        }
    }
}
