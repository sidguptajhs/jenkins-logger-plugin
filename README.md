# Logger Plugin

Currently this plugin provides a replacement for the [`println`][println] and [`echo`][echo] steps in Jenkins
pipelines to allow displaying a proper label in the BlueOcean UI even when the output string contains an Env variable. 


### Why is this needed?

See [https://issues.jenkins-ci.org/browse/JENKINS-53649]


### Example

In the following example, the BlueOcean UI will display `Print Message`
as the step title because the message contains an env var

```groovy
echo "Test: ${JENKINS_NAME}"
```