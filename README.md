![Rubberbands](elasticsearch-apps/raw/master/rubberbands.png)

Elasticsearch Apps
==================

What is Elasticsearch Apps?
---------------------------

Elasticsearch Apps is a tool for instrumenting the Maven artifact dependency mechanism for downloding and resolving Elasticsearch plugins and their dependencies. 

It extends the ``PluginManager``, the ``PluginService`` and the ``PluginsModule`` classes in ``org.elasticsearch.plugins``. Existing Elasticsearch plugins, which are offered as zip file downloads, can be used without modification.

The tool ``bin/apps`` is aimed to replace ``bin/plugins``. It works similar, but uses the JBoss ShrinkWrap resolver project to handle with Maven artifacts in remote repositories.

The config file ``config/elasticsearch.yml`` is enriched by additional entries for declaring the Elasticsearch apps.

Apps are divided into three groups:

- plugin apps, downloaded as a zip file from an URL
- site apps, downloaded as a zip file from an URL
- Maven artifact apps, with dependencies 


How to set it up
----------------

For the Maven part, there is a new configuration file ``config/apps.xml`` which is equivalent to the Maven ``settings.xml``. In this file, Maven repositories for Elasticsearch can be configured. 

This is a verbose sample of a ``config/apps.xml`` which just activates the Maven central repo where the Elasticsearch core plugins already live.

	<?xml version="1.0" encoding="UTF-8"?>
	<!-- see http://maven.apache.org/settings.html -->
	<settings>
		<!-- if you want your Maven local repo equal to Elasticsearch apps local repo -->
		<!-- <localRepository>file://${user.home}/.m2/repository</localRepository> -->
		<!-- Elasticsearch apps local repo-->
		<localRepository>plugins/repository</localRepository>
		<!-- set offline to true to disable any network access when accessing repos. Default is false. -->
		<!--  <offline>true</offline>  -->
		<profiles>
			<!-- 
				The default active profile. Add your remote Maven repositories here.
			-->
			<profile>
				<id>apps</id>
				<activation>
					<activeByDefault>true</activeByDefault>
				</activation>
				<repositories>
					<repository>
						<id>central</id>
						<name>Maven Repository</name>
						<url>http://repo1.maven.org/maven2</url>
						<layout>default</layout>
						<snapshots>
							<enabled>false</enabled>
						</snapshots>
					</repository>
				</repositories>
			</profile>    
		</profiles>	
	</settings>

Now the Maven resolver is ready.

Adding your plugin declarations to Elasticsearch configuration
--------------------------------------------------------------

You can add the plugins you want to declare to be installed by adding some lines to ``config/elasticsearch.yml``. 

This short example declares the icu plugin with a given version:

    apps.dependencies.analysis-icu.dependency: org.elasticsearch:elasticsearch-analysis-icu:1.7.0

This longer example declares the head plugin as a zip download, and a mandatory analysis-icu plugin, where all versions from 1.0.0 until the current version are valid:

	########################### Apps #############################################
	
	apps:
	  mandatory:
		org.elasticsearch:elasticsearch-analysis-icu
	  sites:
		elasticsearch-head:
		  url: https://github.com/mobz/elasticsearch-head/archive/master.zip
		  enabled: true
	  excludes: [
		  "org.elasticsearch:elasticsearch",      
		  "log4j:log4j"
	  ]
	  dependencies:
		analysis-icu:
		  dependency: org.elasticsearch:elasticsearch-analysis-icu:[1.0.0,)
		  enabled: true
	  
That's it. Now, by just starting up an Elasticsearch node, the declared apps are automatically downloaded from remote repository sites and activated.

The command for executing a node in the foreground is

    bin/elasticsearch -f


The ``bin/apps`` tool
---------------------

It is often convenient to investigate for the latest versions of plugins, or looking up what plugins exist without starting a node. The ``bin/apps`` tool is a standalone version of the app service. It's purpose is to offer convenient services to manage plugin apps as a "one stop shop".

You can control the plugin resolving and installation with this tool.

When starting ``bin/apps``, it reads the configuration files, and begins with the download and artifact resolution process, and then opens a console.

Here is a quick overview of the artifact-related commands:

``ls`` - shows a list of the installed Elasticsearch artifact apps, site plugin apps, and legacy plugins

``resolve <artifact>`` - show all dependencies of the artifact, where the artifact is given in Maven canonical form
	
``install <artifact>`` - creates an artifact app out of a dependency by looking for es-plugin.properties on the classpath and instantiating the plugin class
	
``remove <artifact>`` - removes an artifact from the local app repository	
	
``tree <artifact>`` - shows all the dependencies of the given artifact as a tree
	
``whatrequires <artifact>`` - for a given Maven artifact, show all Elasticsearch apps that depend on it (work in progress)

More commands to follow.

Under development
=================

The Elasticsearch apps effort is currently under development. 

For the iterations already done with logfile dumps and info, see

[Iteration 1 - a console-based app tool](elasticsearch-apps/wiki/Iteration-1)

[Iteration 2 - version resolution and new URI classloader](elasticsearch-apps/wiki/Iteration-2)

[Iteration 3 - downloading and installing all Elasticsearch core plugins automatically](elasticsearch-apps/wiki/Iteration-3)

[Iteration 4 - substituting the org.elasticsearch.plugins code in the ES codebase with the app service code](elasticsearch-apps/wiki/Iteration-4)


License
=======

Elasticsearch Apps

Copyright (C) 2012 Jörg Prante

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


References
==========

[Elasticsearch Plugins](http://www.elasticsearch.org/guide/reference/modules/plugins.html)

[Andrew Lee Rubinger about JBoss ShrinkWrap Resolver: INTEGRATION-TESTING NONTRIVIAL DEPLOYMENTS: A SANE MAVEN DEPENDENCY RESOLVER](http://exitcondition.alrubinger.com/2012/09/13/shrinkwrap-resolver-new-api/)

[Maven Version Range Specification](http://maven.apache.org/enforcer/enforcer-rules/versionRanges.html)

[Maven Settings Reference](http://maven.apache.org/settings.html)


