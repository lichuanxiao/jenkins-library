import com.sap.piper.JenkinsUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.RuleChain

import util.BasePiperTest
import util.JenkinsDockerExecuteRule
import util.JenkinsLoggingRule
import util.JenkinsShellCallRule
import util.JenkinsStepRule
import util.PluginMock
import util.Rules

import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse

class DockerExecuteOnKubernetesTest extends BasePiperTest {
    private ExpectedException exception = ExpectedException.none()
    private JenkinsDockerExecuteRule jder = new JenkinsDockerExecuteRule(this)
    private JenkinsShellCallRule jscr = new JenkinsShellCallRule(this)
    private JenkinsLoggingRule jlr = new JenkinsLoggingRule(this)
    private JenkinsStepRule jsr = new JenkinsStepRule(this)

    @Rule
    public RuleChain ruleChain = Rules
        .getCommonRules(this)
        .around(exception)
        .around(jder)
        .around(jscr)
        .around(jlr)
        .around(jsr)
    int whichDockerReturnValue = 0
    def bodyExecuted
    def dockerImage
    def containerMap
    def dockerEnvVars
    def dockerWorkspace
    def podName = ''
    def podLabel = ''
    def containersList = []
    def imageList = []
    def containerName = ''
    def envList = []


    @Before
    void init() {
        containersList = []
        imageList = []
        envList = []
        bodyExecuted = false
        JenkinsUtils.metaClass.static.isPluginActive = { def s -> new PluginMock(s).isActive() }
        helper.registerAllowedMethod('sh', [Map.class], {return whichDockerReturnValue})
        helper.registerAllowedMethod('container', [Map.class, Closure.class], { Map config, Closure body -> container(config){body()}
        })
        helper.registerAllowedMethod('podTemplate', [Map.class, Closure.class], { Map options, Closure body ->
            podName = options.name
            podLabel = options.label
            options.containers.each { option ->
                containersList.add(option.name)
                imageList.add(option.image)
                envList.add(option.envVars)
            }
            body()
        })
        helper.registerAllowedMethod('node', [String.class, Closure.class], { String nodeName, Closure body -> body() })
        helper.registerAllowedMethod('envVar', [Map.class], { Map option -> return option })
        helper.registerAllowedMethod('containerTemplate', [Map.class], { Map option -> return option })
    }

    @Test
    void testRunOnPodNoContainerMapOnlyDockerImage() throws Exception {
        jsr.step.call(script: nullScript,
            dockerImage: 'maven:3.5-jdk-8-alpine',
            dockerOptions: '-it',
            dockerVolumeBind: ['my_vol': '/my_vol'],
            dockerEnvVars: ['http_proxy': 'http://proxy:8000'], dockerWorkspace: '/home/piper'){
                bodyExecuted = true
        }
        assertTrue(containersList.contains('container-exec'))
        assertTrue(imageList.contains('maven:3.5-jdk-8-alpine'))
        assertTrue(envList.toString().contains('http_proxy'))
        assertTrue(envList.toString().contains('http://proxy:8000'))
        assertTrue(envList.toString().contains('/home/piper'))
        assertTrue(bodyExecuted)
    }


    @Test
    void testDockerExecuteOnKubernetesWithCustomContainerMap() throws Exception {
        jsr.step.call(script: nullScript,
            containerMap: ['maven:3.5-jdk-8-alpine': 'mavenexecute']) {
            container(name: 'mavenexecute') {
                bodyExecuted = true
            }
        }
        assertEquals('mavenexecute', containerName)
        assertTrue(containersList.contains('mavenexecute'))
        assertTrue(imageList.contains('maven:3.5-jdk-8-alpine'))
        assertTrue(bodyExecuted)
    }

    @Test
    void testDockerExecuteOnKubernetesWithCustomJnlpWithContainerMap() throws Exception {
        nullScript.commonPipelineEnvironment.configuration = ['general': ['jenkinsKubernetes': ['jnlpAgent': 'myJnalpAgent']]]
        jsr.step.call(script: nullScript,
            containerMap: ['maven:3.5-jdk-8-alpine': 'mavenexecute']) {
            container(name: 'mavenexecute') {
                bodyExecuted = true
            }
        }
        assertEquals('mavenexecute', containerName)
        assertTrue(containersList.contains('mavenexecute'))
        assertTrue(imageList.contains('maven:3.5-jdk-8-alpine'))
        assertTrue(containersList.contains('jnlp'))
        assertTrue(imageList.contains('myJnalpAgent'))
        assertTrue(bodyExecuted)
    }

    @Test
    void testDockerExecuteOnKubernetesWithCustomJnlpWithDockerImage() throws Exception {
        nullScript.commonPipelineEnvironment.configuration = ['general': ['jenkinsKubernetes': ['jnlpAgent': 'myJnalpAgent']]]
        jsr.step.call(script: nullScript,
            dockerImage: 'maven:3.5-jdk-8-alpine') {
            bodyExecuted = true
        }
        assertEquals('container-exec', containerName)
        assertTrue(containersList.contains('jnlp'))
        assertTrue(containersList.contains('container-exec'))
        assertTrue(imageList.contains('myJnalpAgent'))
        assertTrue(imageList.contains('maven:3.5-jdk-8-alpine'))
        assertTrue(bodyExecuted)
    }

    @Test
    void testDockerExecuteOnKubernetesWithCustomWorkspace() throws Exception {
        jsr.step.call(script: nullScript,
            containerMap: ['maven:3.5-jdk-8-alpine': 'mavenexecute'],
            dockerWorkspace: '/home/piper') {
            container(name: 'mavenexecute') {
                bodyExecuted = true
            }
        }
        assertTrue(envList.toString().contains('/home/piper'))
        assertTrue(bodyExecuted)
    }

    @Test
    void testDockerExecuteOnKubernetesWithCustomEnv() throws Exception {
        jsr.step.call(script: nullScript,
            containerMap: ['maven:3.5-jdk-8-alpine': 'mavenexecute'],
            dockerEnvVars: ['customEnvKey': 'customEnvValue']) {
            container(name: 'mavenexecute') {
                bodyExecuted = true
            }
        }
        assertTrue(envList.toString().contains('customEnvKey') && envList.toString().contains('customEnvValue'))
        assertTrue(bodyExecuted)
    }

    @Test
    void testDockerExecuteOnKubernetesUpperCaseContainerName() throws Exception {
        jsr.step.call(script: nullScript,
            containerMap: ['maven:3.5-jdk-8-alpine': 'MAVENEXECUTE'],
            dockerEnvVars: ['customEnvKey': 'customEnvValue']) {
            container(name: 'mavenexecute') {
                bodyExecuted = true
            }
        }
        assertEquals('mavenexecute', containerName)
        assertTrue(containersList.contains('mavenexecute'))
        assertTrue(imageList.contains('maven:3.5-jdk-8-alpine'))
        assertTrue(bodyExecuted)
    }

    @Test
    void testDockerExecuteOnKubernetesEmptyContainerMapNoDockerImage() throws Exception {
        exception.expect(IllegalArgumentException.class);
            jsr.step.call(script: nullScript,
                containerMap: [:],
                dockerEnvVars: ['customEnvKey': 'customEnvValue']) {
                container(name: 'jnlp') {
                    bodyExecuted = true
                }
            }
        assertFalse(bodyExecuted)
    }

    private container(options, body) {
        containerName = options.name
        body()
    }
}
