<div align="center">
<p>
    <img width="200" src="https://raw.githubusercontent.com/CCBlueX/LiquidCloud/master/LiquidBounce/liquidbounceLogo.svg">
</p>

[Website](https://liquidbounce.net) |
[Forum](https://forums.ccbluex.net) |
[Discord](https://liquidbounce.net/discord) |
[YouTube](https://youtube.com/CCBlueX) |
[X](https://x.com/CCBlueX)
</div>

PulseWare es un cliente de Minecraft personalizado basado en Fabric API. Incluye modificaciones y funcionalidades propias, dejando de lado las del proyecto LiquidBounce original.

## Issues

Si encuentras bugs o faltan características, puedes abrir un issue [aquí](https://github.com/CCBlueX/LiquidBounce/issues).

## License

Este proyecto está sujeto a la [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html), aplicable al código fuente ubicado directamente en este repositorio. Durante el desarrollo se pueden usar otras librerías externas que tienen sus propias licencias.

*Acciones permitidas:*

- Usar
- Compartir
- Modificar

*Condiciones al usar código de este proyecto:*

- Debes divulgar el código fuente de cualquier modificación y cualquier código tomado de este proyecto.
- La aplicación modificada también debe estar bajo la licencia GPL.

## Setting up a Workspace

PulseWare usa Gradle; asegúrate de tenerlo instalado correctamente desde [Gradle](https://gradle.org/install/). También requiere [Node.js](https://nodejs.org) para nuestro [tema](https://github.com/CCBlueX/LiquidBounce/tree/nextgen/src-theme).

1. Clona el repositorio usando `git clone --recurse-submodules https://github.com/CCBlueX/LiquidBounce`.
2. Accede a la carpeta del repositorio (`cd LiquidBounce`).
3. Ejecuta `./gradlew genSources` para mejor experiencia de desarrollo (opcional).
4. Abre la carpeta como proyecto Gradle en tu IDE preferido.
5. Ejecuta el cliente con `./gradlew runClient`.

## Additional libraries

### Mixins

Se usan para modificar clases en tiempo de ejecución antes de cargarlas. PulseWare usa mixins para inyectar su código en el cliente de Minecraft sin distribuir código protegido de Mojang. Más información en la [documentación de Mixins](https://docs.spongepowered.org/5.1.0/en/plugin/internals/mixins.html).

## Contributing

Se aceptan contribuciones. Puedes modificar el código de PulseWare y enviar un pull request.

## Stats

![Alt](https://repobeats.axiom.co/api/embed/ad3a9161793c4dfe50934cd4442d25dc3ca93128.svg "Repobeats analytics image")
