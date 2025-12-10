package com.example.proyectofinal

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.pow
import kotlin.math.sqrt


// Clase de la vista personalizada
class GameCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Objetos Paint pinceles para definir estilos de dibujo
    private val objectivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { // Pincel para dibujar el objetivo principal
        style = Paint.Style.FILL // relleno completo
        shader = RadialGradient( // Aplica un degradado radial
            0f, 0f, 100f, // Centro (0,0) y radio de 100
            intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FF4757")), // Colores del degradado
            floatArrayOf(0f, 1f), // Posiciones relativas de los colores
            Shader.TileMode.CLAMP // El color se detiene en el borde
        )
    }

    private val objectiveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { // Pincel para el borde del objetivo
        style = Paint.Style.STROKE //solo trazo/borde
        strokeWidth = 8f // Ancho del borde
        color = Color.WHITE // Color del borde
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { // Pincel para la animacion de onda
        style = Paint.Style.STROKE //  solo trazo
        strokeWidth = 6f // Ancho del trazo.
        color = Color.parseColor("#4ECDC4") // Color de la onda
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { // Pincel para las particulas de efecto visual
        style = Paint.Style.FILL //relleno completo
        color = Color.parseColor("#FFD93D") // Color de las particulas
    }

    // Estado del objetivo actual
    private var currentObjective: ObjectiveRenderData? = null // Datos del objetivo que se esta mostrando actualmente

    // Animaciones
    private val particles = mutableListOf<Particle>() // Lista mutable para guardar las particulas activas
    private var rippleRadius = 0f // Radio actual de la onda expansiva
    private var rippleAlpha = 255 // Opacidad actual de la onda (255 = opaco)
    private var lastFrameTime = System.currentTimeMillis() // Tiempo del ultimo frame dibujado

    // Funcion que se ejecuta cuando el usuario toca el objetivo
    var onObjectiveTapped: ((String) -> Unit)? = null

    // Clase de datos para el objetivo en la vista
    data class ObjectiveRenderData(
        val id: String, // ID del objetivo
        val x: Float, // Posicion X
        val y: Float, // Posicion Y
        val radius: Float, // Radio
        val creationTime: Long // Marca de tiempo de cuando se creo el objetivo
    )

    // Clase de datos para cada particula de la explosion
    data class Particle(
        var x: Float, // Posicion X
        var y: Float, // Posicion Y
        var vx: Float, // Velocidad en X
        var vy: Float, // Velocidad en Y
        var life: Float, // Vida restante de la particula
        var size: Float // Tamaño de la particula
    )

    // Funcion para actualizar el objetivo a dibujar
    fun setObjective(objective: ObjectiveState?) {
        if (objective == null) { // Si el objetivo es nulo
            currentObjective = null // Borra el objetivo actual
            particles.clear() // Limpia cualquier particula activa
            invalidate() // Fuerza el redibujado
            return
        }

        currentObjective = ObjectiveRenderData( // Crea el objeto de datos de renderizado
            id = objective.objectId, // Copia el ID
            x = (objective.normalizedX * width).toFloat(), // Calcula X absoluta
            y = (objective.normalizedY * height).toFloat(), // Calcula Y absoluta
            radius = (objective.radius * width.coerceAtMost(height)).toFloat(), // Calcula radio absoluto
            creationTime = objective.creationTime.time // Marca de tiempo de creacion
        )

        rippleRadius = 0f // Reinicia el radio de la onda
        rippleAlpha = 255 // Reinicia la opacidad de la onda
        invalidate() // Fuerza el redibujado para mostrar el nuevo objetivo
    }

    // Funcion llamada para dibujar la vista
    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas) // Llama a la implementacion base

        val now = System.currentTimeMillis() // Tiempo actual
        val deltaTime = (now - lastFrameTime) / 1000f // Tiempo transcurrido desde el ultimo frame
        lastFrameTime = now // Actualiza el tiempo del ultimo frame

        // Dibujar objetivo actual
        currentObjective?.let { obj -> // Si hay un objetivo
            val timeAlive = now - obj.creationTime // Tiempo que lleva vivo el objetivo
            val progress = (timeAlive / 2500f).coerceIn(0f, 1f) // Progreso de vida 0.0 a 1.0 en 2.5 segundos

            // Animacion de pulso
            val pulseScale = 1f + 0.1f * kotlin.math.sin(progress * Math.PI.toFloat() * 4) // Calcula la escala de pulso
            val radius = obj.radius * pulseScale // Aplica la escala al radio

            // Dibujar sombra
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { // Pincel para la sombra
                color = Color.BLACK
                alpha = 50
                maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL) // Aplica un filtro de desenfoque
            }
            canvas.drawCircle(obj.x + 10f, obj.y + 10f, radius, shadowPaint) // Dibuja la sombra

            // Actualizar shader position
            objectivePaint.shader = RadialGradient( // Recrea el degradado para centrarlo en la posicion actual del objetivo.
                obj.x, obj.y, radius,
                intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#FF4757")),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )

            // Dibujar objetivo
            canvas.drawCircle(obj.x, obj.y, radius, objectivePaint) // Dibuja el circulo con relleno
            canvas.drawCircle(obj.x, obj.y, radius, objectiveStrokePaint) // Dibuja el borde

            // Dibujar indicador de tiempo restante
            val timeLeft = 1f - progress // Tiempo restante
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { // Pincel para el arco de tiempo
                style = Paint.Style.STROKE
                strokeWidth = 12f
                color = when { // Cambia el color del arco segun el tiempo restante
                    timeLeft > 0.5f -> Color.GREEN
                    timeLeft > 0.25f -> Color.YELLOW
                    else -> Color.RED
                }
                strokeCap = Paint.Cap.ROUND // Extremos del arco redondeados
            }

            val arcRadius = radius + 30f // Radio del arco
            val sweepAngle = 360f * timeLeft // angulo a dibujar
            canvas.drawArc( // Dibuja el arco
                obj.x - arcRadius,
                obj.y - arcRadius,
                obj.x + arcRadius,
                obj.y + arcRadius,
                -90f, // angulo inicial
                sweepAngle, // angulo a barrer
                false,
                arcPaint
            )

            // Dibujar ondas expansivas al tocar
            if (rippleAlpha > 0) { // Si la onda todavia es visible
                ripplePaint.alpha = rippleAlpha // Aplica la opacidad
                canvas.drawCircle(obj.x, obj.y, rippleRadius, ripplePaint) // Dibuja el circulo de la onda.
                rippleRadius += 500f * deltaTime // Incrementa el radio
                // Reduce la opacidad a medida que el radio crece.
                rippleAlpha = ((1f - rippleRadius / (radius * 3)) * 255).toInt().coerceAtLeast(0)
                invalidate() // Fuerza el redibujado para continuar la animacion
            }
        }

        // Dibujar particulas efecto de explosion
        val iterator = particles.iterator() // Obtiene un iterador para recorrer la lista de particulas de forma segura
        while (iterator.hasNext()) { // Mientras haya particulas
            val p = iterator.next() // Obtiene la particula actual.

            p.x += p.vx * deltaTime // Mueve X segun la velocidad horizontal y el tiempo
            p.y += p.vy * deltaTime // Mueve Y segun la velocidad vertical y el tiempo
            p.vy += 500f * deltaTime // Aplica gravedad
            p.life -= deltaTime // Reduce la vida restante

            if (p.life > 0) { // Si la particula aun esta viva.
                particlePaint.alpha = (p.life * 255).toInt().coerceIn(0, 255) // Calcula la opacidad segun la vida.
                canvas.drawCircle(p.x, p.y, p.size, particlePaint) // Dibuja la particula
            } else {
                iterator.remove() // Si la vida es <= 0, la elimina de la lista
            }
        }

        // Continuar animacion si hay partículas
        if (particles.isNotEmpty()) { // Si quedan particulas, o si la onda esta activa
            invalidate() // Solicita otro ciclo de dibujo para la animacion
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean { // Maneja los eventos tactiles
        if (event.action == MotionEvent.ACTION_DOWN) { // Si el evento es un toque inicial
            currentObjective?.let { obj -> // Si hay un objetivo activo
                val dx = event.x - obj.x // Diferencia en X entre el toque y el centro del objetivo
                val dy = event.y - obj.y // Diferencia en Y
                val distance = sqrt(dx.pow(2) + dy.pow(2)) // Distancia del toque al centro

                if (distance <= obj.radius) { // Si la distancia es menor o igual al radio
                    // ¡Toque exitoso!
                    createHitParticles(obj.x, obj.y) // Crea el efecto de particulas
                    onObjectiveTapped?.invoke(obj.id) // Llama al callback para notificar el toque
                    rippleRadius = 0f // Reinicia la onda
                    rippleAlpha = 255 // Reinicia la opacidad de la onda
                    invalidate() // Fuerza el redibujado
                    return true
                }
            }
        }
        return super.onTouchEvent(event) // Deja que la vista base maneje otros eventos si no fue un hit
    }

    private fun createHitParticles(x: Float, y: Float) { // Crea las particulas de explosión en x, y
        repeat(20) { // Crea 20 particulas
            val angle = (it / 20f) * 2 * Math.PI // Calcula un angulo para distribuir las particulas en circulo
            val speed = 200f + Math.random().toFloat() * 200f // Velocidad inicial aleatoria

            particles.add( // Añade una nueva particula a la lista
                Particle(
                    x = x,
                    y = y,
                    vx = (kotlin.math.cos(angle) * speed).toFloat(), // Componente X de la velocidad
                    vy = (kotlin.math.sin(angle) * speed).toFloat(), // Componente Y de la velocidad
                    life = 0.5f + Math.random().toFloat() * 0.5f, // Vida aleatoria entre 0.5 y 1.0 segundos
                    size = 8f + Math.random().toFloat() * 8f // Tamaño aleatorio
                )
            )
        }
        invalidate() // Fuerza el redibujado para que las particulas se muestren inmediatamente
    }

    // Se llama cuando el tamaño de la vista cambia.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh) // Llama a la implementacion base

        // Recalcular posiciones si hay un objetivo activo
        currentObjective?.let { obj -> // Si habia un objetivo activo
            val state = ObjectiveState( // Reconstruye el estado logico del objetivo usando proporciones
                objectId = obj.id,
                normalizedX = obj.x.toDouble() / oldw, // Normaliza X usando el ancho viejo
                normalizedY = obj.y.toDouble() / oldh, // Normaliza Y usando el alto viejo
                radius = obj.radius.toDouble() / oldw.coerceAtMost(oldh), // Normaliza el radio
                creationTime = java.util.Date(obj.creationTime),
                isHit = false
            )
            setObjective(state) // Vuelve a establecer el objetivo ahora se recalcula con el nuevo ancho/alto
        }
    }
}