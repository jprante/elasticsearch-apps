/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.apps.console;

import static org.elasticsearch.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;

import java.io.Console;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.Scanner;
import org.elasticsearch.apps.AppService;
import org.elasticsearch.apps.support.ExceptionFormatter;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.internal.InternalSettingsPerparer;

/**
 * A command shell for the App service
 * 
 * @author joerg
 */
public class Shell {

    private static final String NO_CONSOLE = "console not available";
    private static final String UNKNOWN_COMMAND = "unknown command [%1$s]%n";
    private static final String COMMAND_ERROR = "command error [%1$s]: [%2$s]%n";
    private static final String PROMPT = System.getProperty("user.name") + "> ";

    public static void main(String[] args) {
        // this magic line loades config/elasticsearch.yml
        Tuple<Settings, Environment> initialSettings = InternalSettingsPerparer.prepareSettings(EMPTY_SETTINGS, true);
        AppService service = new AppService(initialSettings.v1(), initialSettings.v2());
        Console console = System.console();
        if (console == null) {
            throw new RuntimeException(NO_CONSOLE);
        }
        if (args.length > 0) {
            execCommand(service, args);
        } else {
            execCommandLoop(console, service);
        }
    }

    private static void execCommand(AppService service, String[] args) {
        String url = null;
        for (int i = 0; i < args.length; i++) {
            if ("url".equals(args[i]) || "-url".equals(args[i])) {
                url = args[i + 1];
            }
        }
        for (int c = 0; c < args.length; c++) {
            String command = args[c];
            if (command.equals("help") || command.equals("--help") || command.equals("-help") || command.equals("-h")) {
                System.out.println("Available commands:");
                System.out.println("    -url     [plugins location]  : Set URL to download plugins from");
                System.out.println("    -install [plugin name]       : Downloads and installs listed plugins");
                System.out.println("    -remove  [plugin name]       : Removes listed plugins");
            } else if (command.equals("install") || command.equals("-install")) {
                String pluginName = args[++c];
                System.out.println("-> Installing " + pluginName + "...");
                try {
                    if (service.downloadAndUnpackPlugin(pluginName, new URL(url))) {
                        System.out.println("plugin successfully installed");
                    } else {
                        System.out.println("plugin already exists, remove first");                        
                    }
                } catch (IOException e) {
                    System.out.println("Failed to install " + pluginName + ", reason: " + e.getMessage());
                }
            } else if (command.equals("remove") || command.equals("-remove")) {
                String pluginName = args[++c];
                System.out.println("-> Removing " + pluginName + " ");
                try {
                    service.removePlugin(pluginName);
                } catch (IOException e) {
                    System.out.println("Failed to remove " + pluginName + ", reason: " + e.getMessage());
                }
            } else {
                c++;
            }
        }
    }

    private static void execCommandLoop(final Console console, AppService service) {
        while (true) {
            String commandLine = console.readLine(PROMPT, new Date());
            if (commandLine == null) {
                Command.EXIT.exec(console, service, null, null);
            }
            Scanner scanner = new Scanner(commandLine);
            if (scanner.hasNext()) {
                final String commandName = scanner.next().toUpperCase();
                try {
                    final Command cmd = Enum.valueOf(Command.class, commandName);
                    LinkedList<String> params = Lists.newLinkedList();
                    while (scanner.hasNext()) {
                        params.add(scanner.next());
                    }
                    cmd.exec(console, service, params, new Command.Listener() {
                        @Override
                        public void exception(Exception e) {
                            console.printf(COMMAND_ERROR, cmd, ExceptionFormatter.format(e));
                        }
                    });
                } catch (IllegalArgumentException e) {
                    console.printf(UNKNOWN_COMMAND, commandName);
                }
            }
            scanner.close();
        }
    }
}