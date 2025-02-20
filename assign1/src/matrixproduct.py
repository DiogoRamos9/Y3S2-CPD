import time

def OnMult(m_ar, m_br):
    # Initialize matrices
    pha = [1.0] * (m_ar * m_ar)
    phb = [(i + 1) for i in range(m_br) for _ in range(m_br)]
    phc = [0.0] * (m_ar * m_ar)

    # Start timing
    start_time = time.time()

    # Matrix multiplication
    for i in range(m_ar):
        for j in range(m_br):
            temp = 0.0
            for k in range(m_ar):
                temp += pha[i * m_ar + k] * phb[k * m_br + j]
            phc[i * m_ar + j] = int(temp)

    # End timing
    end_time = time.time()
    elapsed_time = end_time - start_time
    print(f"Time: {elapsed_time:.3f} seconds")

    # Display 10 elements of the result matrix to verify correctness
    print("Result matrix:")
    for i in range(1):
        for j in range(min(10, m_br)):
            print(phc[j], end=" ")
    print()
    

def OnMultLine(m_ar, m_br):
    # Initialize matrices
    pha = [1.0] * (m_ar * m_ar)
    phb = [(i + 1) for i in range(m_br) for _ in range(m_br)]
    phc = [0.0] * (m_ar * m_ar)

    # Start timing
    start_time = time.time()

    for i in range (m_ar):
        for k in range (m_br):
            for j in range (m_br):
                phc[i*m_ar+j] += int(pha[i*m_ar+k]*phb[k*m_br+j])

    # End timing
    end_time = time.time()
    elapsed_time = end_time - start_time
    print(f"Time: {elapsed_time:.3f} seconds")

    # Display 10 elements of the result matrix to verify correctness
    print("Result matrix:")
    for i in range(1):
        for j in range(min(10, m_br)):
            print(phc[j], end=" ")
    print()
    


def OnMultBlock(m_ar, m_br, blockSize):
    # Initialize matrices
    pha = [1.0] * (m_ar * m_ar)
    phb = [(i + 1) for i in range(m_br) for _ in range(m_br)]
    phc = [0] * (m_ar * m_ar)

    # Start timing
    start_time = time.time()

    # Block Multiplication
    for i0 in range(0, m_ar, blockSize):
        for j0 in range(0, m_br, blockSize):
            for k0 in range(0, m_ar, blockSize):
                for i in range(i0, min(i0 + blockSize, m_ar)):
                    for j in range(j0, min(j0 + blockSize, m_br)):
                        temp = 0.0
                        for k in range(k0, min(k0 + blockSize, m_ar)):
                            temp += pha[i * m_ar + k] * phb[k * m_br + j]
                        phc[i * m_ar + j] += temp

    # End timing
    end_time = time.time()
    elapsed_time = end_time - start_time
    print(f"Time: {elapsed_time:.3f} seconds")

    # Display 10 elements of the result matrix to verify correctness
    print("Result matrix:")
    for i in range(1):
        for j in range(min(10, m_br)):
            print(phc[j], end=" ")
    print()

while True:
    print("1. Multiplication")
    print("2. Line Multiplication")
    print("3. Block Multiplication")
    op = int(input("Selection?: "))
    if op == 0:
        break
    lin = int(input("Dimensions: lins=cols ? "))
    col = lin
    match op:
        case 1:
            OnMult(lin,col)
        case 2:
            OnMultLine(lin, col)
        case 3:
            blockSize = int(input("Block Size? "))
            OnMultBlock(lin, col, blockSize)

        