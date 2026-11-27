Quiero que mejorar el módulo de orquestación y el módulo de  profiling. En el módulo de orquestación quiero que se añada los scripts necesarios (minimizar la cantidad y complejidad) para:
- Medir el uso de memoria, procesador, y almacenamiento, además de el tiempo de cada una de las fases de los TJobs.
- Exportar esas mediciones a un fichero de datos, para luego poder procesarlo.

En el modulo de profiling, quiero que se pueda calcular los perfiles de uso. La salida va a ser un dashboard interactivo que muestre los datos de la ejecución del sistema. Tiene que obtener de forma dinámica los costes de varios proveedores cloud, disponer de distintas alternativas (MVs, contenedores, etc). Cuando  tenemos una alternativa, muestra el coste y cómo este se divide entre las distintas fases de los TJObs y el objeto cloud.

Ajusta las pruebas, preguntame las decisiones arquitectónicas y de implementación segun van emergiendo.

Quiero tambien lo siguiente: conocer cuantos recursos (memoria, procesador, almacenamiento...) consumen cada contenedor (recurso) que se despliega. Quiero que al pulsar en cada TJob se muestre el análisis por cada recurso. Quiero que el tamaño de las imágenes también se contabilicen para el almacenamiento usado (Además de los volúmenes).Quiero que todo esta información se guarde en el csv y se presente en el perfil de recursos.

