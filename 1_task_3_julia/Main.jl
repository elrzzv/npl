const WIDTH  = 800
const HEIGHT = 600
const MAX_ITER = 1000
const X_MIN = -2.5
const X_MAX =  1.0
const Y_MIN = -1.25
const Y_MAX =  1.25

# z = z*z + c
# c = cr + ci·i
function mandel(cr::Float64, ci::Float64)::Int
    zr = 0.0
    zi = 0.0
    for n in 1:MAX_ITER
        zr2 = zr * zr
        zi2 = zi * zi
        zr2 + zi2 > 4.0 && return n - 1
        zi = 2.0 * zr * zi + ci
        zr = zr2 - zi2 + cr
    end
    MAX_ITER
end

function colorize(n::Int)::NTuple{3, UInt8}
    n >= MAX_ITER && return (0x00, 0x00, 0x00)
    t = n / MAX_ITER
    r = round(UInt8, 127.5 * (1.0 + sin(2pi * t)))
    g = round(UInt8, 127.5 * (1.0 + sin(2pi * t + 2.094)))
    b = round(UInt8, 127.5 * (1.0 + sin(2pi * t + 4.188)))
    (r, g, b)
end

function plane(x::Int, y::Int, w::Int, h::Int)::Tuple{Float64, Float64}
    cr = X_MIN + (x - 1) / (w - 1) * (X_MAX - X_MIN)
    ci = Y_MAX - (y - 1) / (h - 1) * (Y_MAX - Y_MIN)
    (cr, ci)
end

function render_serial!(buf::Vector{UInt8}, w::Int, h::Int)
    for y in 1:h
        for x in 1:w
            cr, ci = plane(x, y, w, h)
            r, g, b = colorize(mandel(cr, ci))
            i = 3 * ((y - 1) * w + (x - 1))
            buf[i + 1] = r
            buf[i + 2] = g
            buf[i + 3] = b
        end
    end
    buf
end

function render_threads!(buf::Vector{UInt8}, w::Int, h::Int)
    Threads.@threads for y in 1:h
        for x in 1:w
            cr, ci = plane(x, y, w, h)
            r, g, b = colorize(mandel(cr, ci))
            i = 3 * ((y - 1) * w + (x - 1))
            buf[i + 1] = r
            buf[i + 2] = g
            buf[i + 3] = b
        end
    end
    buf
end

function write_ppm(path::String, buf::Vector{UInt8}, w::Int, h::Int)
    open(path, "w") do io
        write(io, "P6\n$(w) $(h)\n255\n")
        write(io, buf)
    end
end

function main()
    println("=== Mandelbrot $(WIDTH)x$(HEIGHT), max iterations = $MAX_ITER ===")
    println("Threads: $(Threads.nthreads())")

    # Прогрев компиляции, чтобы время не включало JIT
    tiny = Vector{UInt8}(undef, 3 * 8 * 6)
    render_serial!(tiny, 8, 6)
    render_threads!(copy(tiny), 8, 6)

    # 1 поток
    buf1 = Vector{UInt8}(undef, WIDTH * HEIGHT * 3)
    t1 = @elapsed render_serial!(buf1, WIDTH, HEIGHT)
    println("Serial:  $(round(t1, digits = 3)) s")

    # N потоков
    bufN = Vector{UInt8}(undef, WIDTH * HEIGHT * 3)
    tN = @elapsed render_threads!(bufN, WIDTH, HEIGHT)
    println("Parallel ($(Threads.nthreads()) threads): $(round(tN, digits = 3)) s")
    println("Speedup: $(round(t1 / tN, digits = 2))x")

    buf1 == bufN || error("Serial and parallel buffers differ")
    println("Buffers match: $(buf1 == bufN ? "OK" : "FAIL")")

    out = joinpath(@__DIR__, "mandelbrot.ppm")
    write_ppm(out, bufN, WIDTH, HEIGHT)
    println("Saved: mandelbrot.ppm ($(filesize(out)) bytes)")
end

main()
