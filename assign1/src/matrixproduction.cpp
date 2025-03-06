#include <stdio.h>
#include <iostream>
#include <iomanip>
#include <time.h>
#include <cstdlib>
#include <papi.h>
#include <omp.h>

using namespace std;

#define SYSTEMTIME clock_t

 
void OnMult(int m_ar, int m_br) 
{
	
	SYSTEMTIME Time1, Time2; 
	
	char st[100];
	double temp;
	int i, j, k;

	double *pha, *phb, *phc;

	// A * B = C
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

	// Matrix A is just 1's
	for(i=0; i<m_ar; i++)
		for(j=0; j<m_ar; j++)
			pha[i*m_ar + j] = (double)1.0;

	// Matrix B is a matrix with each cell being the value of the corresponding line (starting by 1)
	for(i=0; i<m_br; i++)
		for(j=0; j<m_br; j++)
			phb[i*m_br + j] = (double)(i+1);

    Time1 = clock();

	// Multiplication
	for(i=0; i<m_ar; i++)
	{	for( j=0; j<m_br; j++)
		{	temp = 0;
			for( k=0; k<m_ar; k++)
			{	
				temp += pha[i*m_ar+k] * phb[k*m_br+j];
			}
			phc[i*m_ar+j]=temp;
		}
	}


    Time2 = clock();
	sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
	cout << st;

	// display 10 elements of the result matrix tto verify correctness
	cout << "Result matrix: " << endl;
	for(i=0; i<1; i++)
	{	for(j=0; j<min(10,m_br); j++)
			cout << phc[j] << " ";
	}
	cout << endl;

    free(pha);
    free(phb);
    free(phc);
	
	
}

// Line Multiplication Code
void OnMultLine(int m_ar, int m_br)
{
    SYSTEMTIME Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;
    
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    // Initialize result matrix to zero
    for(i=0; i<m_ar; i++)
        for(j=0; j<m_ar; j++)
            phc[i*m_ar + j] = 0.0;

    // Matrix A is just 1's
    for(i=0; i<m_ar; i++)
        for(j=0; j<m_ar; j++)
            pha[i*m_ar + j] = (double)1.0;

    // Matrix B is a matrix with each cell being the value of the corresponding line (starting by 1)
    for(i=0; i<m_br; i++)
        for(j=0; j<m_br; j++)
            phb[i*m_br + j] = (double)(i+1);

    Time1 = clock();

    // Line Multiplication
    for(i=0; i<m_ar; i++)
    {   
        for(k=0; k<m_ar; k++)
        {
            for(j=0; j<m_br; j++)
            {   
                phc[i*m_ar+j] += pha[i*m_ar+k] * phb[k*m_br+j];
            }
        }
    }

    Time2 = clock();
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    // display 10 elements of the result matrix to verify correctness
    cout << "Result matrix: " << endl;
    for(i=0; i<1; i++)
    {   
        for(j=0; j<min(10,m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

// Block Multiplication Code
void OnMultBlock(int m_ar, int m_br, int bkSize)
{
	SYSTEMTIME Time1, Time2;

	char st[100];
	double temp;
	int i, j, k, i0, j0, k0;

	double *pha, *phb, *phc;

	// A * B = C
	pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
	phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    // Initialize result matrix to zero
    for(i=0; i<m_ar; i++)
        for(j=0; j<m_ar; j++)
            phc[i*m_ar + j] = 0.0;

	// Matrix A is just 1's
	for(i=0; i<m_ar; i++)
		for(j=0; j<m_ar; j++)
			pha[i*m_ar + j] = (double)1.0;

	// Matrix B is a matrix with each cell being the value of the corresponding line (starting by 1)
	for(i=0; i<m_br; i++)
		for(j=0; j<m_br; j++)
			phb[i*m_br + j] = (double)(i+1);

	Time1 = clock();

	// Block Multiplication
	// Block Multiplication
	for (i0 = 0; i0 < m_ar; i0 += bkSize) {
		for (k0 = 0; k0 < m_ar; k0 += bkSize) {
			for (j0 = 0; j0 < m_br; j0 += bkSize)  {
				for (i = i0; i < min(i0 + bkSize, m_ar); i++) {
					for (k = k0; k < min(k0 + bkSize, m_ar); k++) {
						for (j = j0; j < min(j0 + bkSize, m_br); j++) {
							phc[i * m_ar + j] += pha[i * m_ar + k] * phb[k * m_br + j];
						}
						
					}
				}
			}
		}
	}


	Time2 = clock();
	sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) / CLOCKS_PER_SEC);
	cout << st;

	// display 10 elements of the result matrix to verify correctness
	cout << "Result matrix: " << endl;
	for(i=0; i<1; i++)
	{	for(j=0; j<min(10,m_br); j++)
			cout << phc[j] << " ";
	}
	cout << endl;

	free(pha);
	free(phb);
	free(phc);
    
}

void OnMultLineParallel(int m_ar, int m_br)
{
    double Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;
    
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    // Initialize result matrix to zero
    for(i=0; i<m_ar; i++)
        for(j=0; j<m_ar; j++)
            phc[i*m_ar + j] = 0.0;

    // Matrix A is just 1's
    for(i=0; i<m_ar; i++)
        for(j=0; j<m_ar; j++)
            pha[i*m_ar + j] = (double)1.0;

    // Matrix B is a matrix with each cell being the value of the corresponding line (starting by 1)
    for(i=0; i<m_br; i++)
        for(j=0; j<m_br; j++)
            phb[i*m_br + j] = (double)(i+1);

    Time1 = omp_get_wtime();

    // Line Multiplication
	# pragma omp parallel for
    for(i=0; i<m_ar; i++)
    {   
        for(k=0; k<m_ar; k++)
        {
            for(j=0; j<m_br; j++)
            {   
                phc[i*m_ar+j] += pha[i*m_ar+k] * phb[k*m_br+j];
            }
        }
    }

    Time2 = omp_get_wtime();
	
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) );
    cout << st;

    
    cout << "Result matrix: " << endl;
    for(i=0; i<1; i++)
    {   
        for(j=0; j<min(10,m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

void OnMultLineParallelV2(int m_ar, int m_br)
{
	double Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;
    
    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    // Initialize result matrix to zero
    for(i=0; i<m_ar; i++)
        for(j=0; j<m_ar; j++)
            phc[i*m_ar + j] = 0.0;

    // Matrix A is just 1's
    for(i=0; i<m_ar; i++)
        for(j=0; j<m_ar; j++)
            pha[i*m_ar + j] = (double)1.0;

    // Matrix B is a matrix with each cell being the value of the corresponding line (starting by 1)
    for(i=0; i<m_br; i++)
        for(j=0; j<m_br; j++)
            phb[i*m_br + j] = (double)(i+1);

    Time1 = omp_get_wtime();

    // Line Multiplication
	# pragma omp parallel
    for(i=0; i<m_ar; i++)
    {   
        for(k=0; k<m_ar; k++)
        {	
			# pragma omp for
            for(j=0; j<m_br; j++)
            {   
                phc[i*m_ar+j] += pha[i*m_ar+k] * phb[k*m_br+j];
            }
        }
    }

    Time2 = omp_get_wtime();
	
    sprintf(st, "Time: %3.3f seconds\n", (double)(Time2 - Time1) );
    cout << st;

    
    cout << "Result matrix: " << endl;
    for(i=0; i<1; i++)
    {   
        for(j=0; j<min(10,m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}


/*
void handle_error (int retval)
{
  printf("PAPI error %d: %s\n", retval, PAPI_strerror(retval));
  exit(1);
}

void init_papi() {
  int retval = PAPI_library_init(PAPI_VER_CURRENT);
  if (retval != PAPI_VER_CURRENT && retval < 0) {
    printf("PAPI library version mismatch!\n");
    exit(1);
  }
  if (retval < 0) handle_error(retval);

  std::cout << "PAPI Version Number: MAJOR: " << PAPI_VERSION_MAJOR(retval)
            << " MINOR: " << PAPI_VERSION_MINOR(retval)
            << " REVISION: " << PAPI_VERSION_REVISION(retval) << "\n";
}
*/ 

int main (int argc, char *argv[])
{		
	
	//int EventSet = PAPI_NULL;
  	long long values[2];
  	int ret;
	
	/*
	ret = PAPI_library_init( PAPI_VER_CURRENT );
	if ( ret != PAPI_VER_CURRENT )
		std::cout << "FAIL" << endl;


	ret = PAPI_create_eventset(&EventSet);
		if (ret != PAPI_OK) cout << "ERROR: create eventset" << endl;


	ret = PAPI_add_event(EventSet,PAPI_L1_DCM );
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L1_DCM" << endl;


	ret = PAPI_add_event(EventSet,PAPI_L2_DCM);
	if (ret != PAPI_OK) cout << "ERROR: PAPI_L2_DCM" << endl; 
	*/
	char c;
	int lin, col, blockSize;
	int op;
	op = 1;
	do {
		cout << endl << "1. Multiplication" << endl;
		cout << "2. Line Multiplication" << endl;
		cout << "3. Block Multiplication" << endl;
		cout << "4. Line Multiplication Parallel V1" << endl;
		cout << "5. Line Multiplication Parallel V2" << endl;
		cout << "0. Exit" << endl;
		cout << "Selection?: ";
		cin >>op;
		if (op == 0)
			break;
		printf("Dimensions: lins=cols ? ");
   		cin >> lin;
   		col = lin;


		// Start counting
		//ret = PAPI_start(EventSet);
		//if (ret != PAPI_OK) cout << "ERROR: Start PAPI" << endl;

		switch (op){
			case 1: 
				OnMult(lin, col);
				break;
			case 2:
				OnMultLine(lin, col);  
				break;
			case 3:
				cout << "Block Size? ";
				cin >> blockSize;
				OnMultBlock(lin, col, blockSize);  
				break;
			case 4:
				OnMultLineParallel(lin, col);
				break;
			case 5:
				OnMultLineParallelV2(lin, col);
				break;

		}
		/*
  		ret = PAPI_stop(EventSet, values);
  		if (ret != PAPI_OK) cout << "ERROR: Stop PAPI" << endl;
  		printf("L1 DCM: %lld \n",values[0]);
  		printf("L2 DCM: %lld \n",values[1]);

		ret = PAPI_reset( EventSet );
		if ( ret != PAPI_OK )
			std::cout << "FAIL reset" << endl;
		*/



	}
	while (op != 0);
	/*
	ret = PAPI_remove_event( EventSet, PAPI_L1_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_remove_event( EventSet, PAPI_L2_DCM );
	if ( ret != PAPI_OK )
		std::cout << "FAIL remove event" << endl; 

	ret = PAPI_destroy_eventset( &EventSet );
	if ( ret != PAPI_OK )
		std::cout << "FAIL destroy" << endl;
	*/
}